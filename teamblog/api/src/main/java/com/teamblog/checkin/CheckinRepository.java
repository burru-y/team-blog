package com.teamblog.checkin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CheckinRepository extends JpaRepository<Checkin, UUID> {

    boolean existsByUserIdAndCheckinDate(UUID userId, LocalDate checkinDate);

    /** 连续段判定用：一次取回近 N 天，避免逐天查询。 */
    List<Checkin> findByUserIdAndCheckinDateBetween(UUID userId, LocalDate from, LocalDate to);

    List<Checkin> findByCheckinDateBetween(LocalDate from, LocalDate to);

    /** 热力图用：按天聚合打卡人数（团队视角）。 */
    @Query("""
            select c.checkinDate as checkinDate, count(c) as total
              from Checkin c
             where c.checkinDate between :from and :to
             group by c.checkinDate
             order by c.checkinDate
            """)
    List<DailyCount> countByDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 投影接口：只取两列，不加载整行。 */
    interface DailyCount {
        LocalDate getCheckinDate();

        long getTotal();
    }
}
