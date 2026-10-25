package com.teamblog.announcement;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 公告的对外表示。authorName 只有管理员列表才需要，公开列表不带。 */
public record AnnouncementResponse(
        UUID id,
        String kind,
        String version,
        String title,
        String bodyMd,
        boolean pinned,
        OffsetDateTime publishedAt
) {

    public static AnnouncementResponse from(Announcement announcement) {
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getKind().name(),
                announcement.getVersion(),
                announcement.getTitle(),
                announcement.getBodyMd(),
                announcement.isPinned(),
                announcement.getPublishedAt()
        );
    }
}
