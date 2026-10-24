package com.teamblog.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 邀请仓储。
 *
 * <p>关键方法是 {@link #markUsedIfUnused}：一条**带条件的原子更新**。
 *
 * <pre>
 *   update admin_invites set used_at = now() where id = ? and used_at is null
 * </pre>
 *
 * 两个人同时提交同一张邀请时，数据库保证只有一条语句能更新成功（行锁 + 条件判断），
 * 返回 0 就说明"被抢先了"。如果改成"先查再改"，两个事务都会读到 `used_at is null`，
 * 于是都创建用户——这就是经典的检查后使用（TOCTOU）竞态。
 */
public interface AdminInviteRepository extends JpaRepository<AdminInvite, UUID> {

    Optional<AdminInvite> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update AdminInvite i set i.usedAt = :now where i.id = :id and i.usedAt is null")
    int markUsedIfUnused(@Param("id") UUID id, @Param("now") OffsetDateTime now);
}
