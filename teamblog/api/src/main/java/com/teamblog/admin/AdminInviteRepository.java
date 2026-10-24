package com.teamblog.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 邀请的持久化。
 *
 * <p>关键方法是 {@link #markUsedIfUnused}：一条带条件的原子更新。
 *
 * <pre>
 *   update admin_invites set used_at = :now
 *    where id = :id and used_at is null and revoked_at is null
 * </pre>
 *
 * 两个人同时提交同一张邀请时，数据库保证只有一条语句能更新成功（行锁加条件判断），
 * 所以并发下只会有一个账号被建出来。
 */
public interface AdminInviteRepository extends JpaRepository<AdminInvite, UUID> {

    Optional<AdminInvite> findByTokenHash(String tokenHash);

    /** 管理员列表用：最近的 100 张。量不大，不做分页。 */
    List<AdminInvite> findTop100ByOrderByCreatedAtDesc();

    // clearAutomatically：批量更新不会同步一级缓存里的实体，清掉避免后续读到旧状态
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AdminInvite i
               set i.usedAt = :now
             where i.id = :id and i.usedAt is null and i.revokedAt is null
            """)
    int markUsedIfUnused(@Param("id") UUID id, @Param("now") OffsetDateTime now);
}