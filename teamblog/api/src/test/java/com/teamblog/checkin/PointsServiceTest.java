package com.teamblog.checkin;

import com.teamblog.checkin.dto.CheckinResult;
import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.points.PointEventKind;
import com.teamblog.points.PointService;
import com.teamblog.support.MutableClock;
import com.teamblog.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 打卡与连续段奖励（批次 4.2，对应实施方案 §4.5 的用例表）。
 *
 * <p>为什么这一批要写成纯单测而不是集成测试：规则全是**日期算术**——
 * 连续 7/14/30 天、断签、跨时区。用可拨时钟 + 桩仓储，可以把 30 天压缩到毫秒级跑完，
 * 也不用为了测一个边界去改数据库。
 */
class PointsServiceTest {

    private static final UUID USER = UUID.fromString("0192f0c4-0000-7000-8000-000000000001");

    private CheckinRepository checkinRepository;
    private PointService pointService;
    private UserRepository userRepository;
    private MutableClock clock;
    private CheckinService service;

    @BeforeEach
    void setUp() {
        checkinRepository = mock(CheckinRepository.class);
        pointService = mock(PointService.class);
        userRepository = mock(UserRepository.class);
        clock = MutableClock.atStartOfDay(LocalDate.of(2026, 3, 1));
        service = new CheckinService(checkinRepository, pointService, userRepository, clock);

        when(checkinRepository.saveAndFlush(any(Checkin.class))).thenAnswer(i -> i.getArgument(0));
        when(pointService.award(any(), any(), anyString(), anyInt())).thenReturn(true);
        when(userRepository.findTotalPointsById(any())).thenReturn(Optional.of(1));
    }

    @Test
    @DisplayName("单次打卡：+1，streak=1")
    void 单次打卡() {
        givenCheckins(today());

        CheckinResult result = service.checkin(USER, null);

        assertThat(result.streak()).isEqualTo(1);
        assertThat(result.gained()).isEqualTo(1);
        verify(pointService).award(eq(USER), eq(PointEventKind.CHECKIN), anyString(), eq(1));
        verify(pointService, never()).award(eq(USER), eq(PointEventKind.STREAK_7), anyString(), anyInt());
    }

    @Test
    @DisplayName("连续第 7 天：+1+5，streak=7，奖励 refId 是本段第一天")
    void 连续七天() {
        LocalDate start = today().minusDays(6);
        givenCheckins(start, start.plusDays(1), start.plusDays(2), start.plusDays(3),
                start.plusDays(4), start.plusDays(5), today());

        CheckinResult result = service.checkin(USER, null);

        assertThat(result.streak()).isEqualTo(7);
        assertThat(result.gained()).isEqualTo(6);
        verify(pointService).award(USER, PointEventKind.STREAK_7, start.toString(), 5);
    }

    @Test
    @DisplayName("连续第 8~13 天：每天 +1，无额外奖励")
    void 第八到十三天() {
        givenContinuousDays(10);

        CheckinResult result = service.checkin(USER, null);

        assertThat(result.streak()).isEqualTo(10);
        assertThat(result.gained()).isEqualTo(1);
        verify(pointService, never()).award(eq(USER), eq(PointEventKind.STREAK_7), anyString(), anyInt());
        verify(pointService, never()).award(eq(USER), eq(PointEventKind.STREAK_14), anyString(), anyInt());
    }

    @Test
    @DisplayName("连续第 14 天：+1+10")
    void 连续十四天() {
        LocalDate start = today().minusDays(13);
        givenContinuousDays(14);

        CheckinResult result = service.checkin(USER, null);

        assertThat(result.streak()).isEqualTo(14);
        assertThat(result.gained()).isEqualTo(11);
        verify(pointService).award(USER, PointEventKind.STREAK_14, start.toString(), 10);
    }

    @Test
    @DisplayName("连续第 30 天：+1+20")
    void 连续三十天() {
        LocalDate start = today().minusDays(29);
        givenContinuousDays(30);

        CheckinResult result = service.checkin(USER, null);

        assertThat(result.streak()).isEqualTo(30);
        assertThat(result.gained()).isEqualTo(21);
        verify(pointService).award(USER, PointEventKind.STREAK_30, start.toString(), 20);
    }

    @Test
    @DisplayName("断 1 天后再连 7 天：新的一段，可以再拿 +5")
    void 断签后重新连续七天() {
        // 本段从 today-6 开始（前天断的），上一段已经发过 STREAK_7，但 refId 不同
        LocalDate newStart = today().minusDays(6);
        givenCheckins(newStart, newStart.plusDays(1), newStart.plusDays(2), newStart.plusDays(3),
                newStart.plusDays(4), newStart.plusDays(5), today());

        service.checkin(USER, null);

        verify(pointService).award(USER, PointEventKind.STREAK_7, newStart.toString(), 5);
    }

    @Test
    @DisplayName("重复打卡：409，不再加分")
    void 重复打卡() {
        doThrow(new DataIntegrityViolationException("uq_checkins_user_date"))
                .when(checkinRepository).saveAndFlush(any(Checkin.class));

        assertThatThrownBy(() -> service.checkin(USER, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ALREADY_CHECKED_IN);

        verify(pointService, never()).award(any(), any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("跨时区：UTC 15:30 = 上海 23:30，记在上海当天")
    void 上海当天深夜() {
        clock.setInstant(Instant.parse("2026-03-01T15:30:00Z"));
        givenCheckins(LocalDate.of(2026, 3, 1));

        CheckinResult result = service.checkin(USER, null);

        assertThat(result.checkinDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    @DisplayName("跨时区：UTC 16:30 = 上海次日 00:30，记在次日")
    void 上海次日凌晨() {
        clock.setInstant(Instant.parse("2026-03-01T16:30:00Z"));
        givenCheckins(LocalDate.of(2026, 3, 2));

        CheckinResult result = service.checkin(USER, null);

        assertThat(result.checkinDate()).isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    @DisplayName("时钟是上海时区，不是 UTC")
    void 时钟时区() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(Instant.now(clock).atZone(ZoneOffset.UTC).toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    // ---------- 辅助 ----------

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    /** 从 today 往回连续 n 天（含今天）。 */
    private void givenContinuousDays(int n) {
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            dates.add(today().minusDays(i));
        }
        givenCheckins(dates.toArray(new LocalDate[0]));
    }

    private void givenCheckins(LocalDate... dates) {
        List<Checkin> rows = Arrays.stream(dates)
                .map(date -> Checkin.create(USER, date, null))
                .toList();
        when(checkinRepository.findByUserIdAndCheckinDateBetween(eq(USER), any(), any())).thenReturn(rows);
    }
}
