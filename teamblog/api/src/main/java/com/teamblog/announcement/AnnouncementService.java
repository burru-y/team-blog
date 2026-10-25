package com.teamblog.announcement;

import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.common.UuidUtils;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 公告与更新日志。
 *
 * <p>发布只能由管理员做（URL 规则拦在 /api/v1/admin/**），
 * 读取是公开的——访客进站第一眼就该知道站点最近有什么变化。
 */
@Service
public class AnnouncementService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_TITLE = 200;
    private static final int MAX_BODY = 20000;

    private final AnnouncementRepository repository;

    public AnnouncementService(AnnouncementRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> listAll() {
        return repository.findAllByOrderByPinnedDescPublishedAtDesc().stream()
                .map(AnnouncementResponse::from)
                .toList();
    }

    /** 版本时间线用：只要更新日志。 */
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> listChangelogs() {
        return repository.findByKindOrderByPublishedAtDesc(Announcement.Kind.CHANGELOG).stream()
                .map(AnnouncementResponse::from)
                .toList();
    }

    @Transactional
    public AnnouncementResponse create(UUID authorId, CreateAnnouncementRequest request) {
        Announcement.Kind kind = parseKind(request.kind());
        String title = requireText(request.title(), "标题", MAX_TITLE);
        String body = requireText(request.bodyMd(), "正文", MAX_BODY);

        String version = request.version() == null ? null : request.version().trim();
        if (version != null && version.isEmpty()) {
            version = null;
        }
        if (kind == Announcement.Kind.CHANGELOG && version == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "更新日志必须填写版本号");
        }
        // 版本号格式统一成 v1.1：允许用户分别填 1.1 / v1.1，出不统一的前缀会让时间线很乱
        if (version != null && !version.startsWith("v")) {
            version = "v" + version;
        }

        Announcement saved = repository.save(Announcement.create(
                UuidUtils.v7(), kind, version, title, body,
                Boolean.TRUE.equals(request.pinned()), authorId, OffsetDateTime.now(ZONE)));

        return AnnouncementResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        repository.deleteById(id);
    }

    private static Announcement.Kind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return Announcement.Kind.NOTICE;
        }
        try {
            return Announcement.Kind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "类型只能是 NOTICE 或 CHANGELOG");
        }
    }

    private static String requireText(String raw, String what, int max) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, what + "不能为空");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > max) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, what + "不能超过 " + max + " 字");
        }
        return trimmed;
    }

    /** 新建请求：kind 缺省为 NOTICE，title/body 必填。 */
    public record CreateAnnouncementRequest(
            String kind,
            String version,
            String title,
            String bodyMd,
            Boolean pinned
    ) {
    }
}
