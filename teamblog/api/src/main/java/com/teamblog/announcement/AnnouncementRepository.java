package com.teamblog.announcement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    /** 首页与公告页共用：置顶优先，其余按发布时间倒序。 */
    List<Announcement> findAllByOrderByPinnedDescPublishedAtDesc();

    /** 只取更新日志，给版本时间线用。 */
    List<Announcement> findByKindOrderByPublishedAtDesc(Announcement.Kind kind);
}
