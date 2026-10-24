package com.teamblog.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * refresh token 仓储。
 *
 * <p>所有查询都按 {@code token_hash} 或 {@code user_id} 走索引；没有"查全部再在内存里筛"
 * 的方法——会话表会随时间增长，全表扫描总有一天会变成慢查询（D-17）。
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    /**
     * 清理任务用：删掉已经过期超过保留期的记录。
     *
     * <p>为什么批量删除而不是逐个：清理任务每天只跑一次，但一次可能几万行，
     * 单条 delete 会把事务拉得很长（D-16）。
     */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") OffsetDateTime threshold);
}
