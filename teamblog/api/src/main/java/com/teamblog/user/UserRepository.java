package com.teamblog.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 账号仓储。只有数据访问，不含业务判断（J-02）。
 *
 * <p>为什么同时提供 "active" 与裸查询：登录、个人页、邀请校验都必须把软删账号当成
 * 不存在（D-13）；但"用户名/邮箱是否被占用"要看真实占用情况——如果只查未删的，一个被
 * 软删的账号会让同名新人注册成功，随后在唯一键上炸掉。
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /** 含已软删：用于后台排查，禁止直接用于登录。 */
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    @Query("select u from User u where u.username = :username and u.deletedAt is null")
    Optional<User> findActiveByUsername(@Param("username") String username);

    @Query("select u from User u where u.email = :email and u.deletedAt is null")
    Optional<User> findActiveByEmail(@Param("email") String email);

    /**
     * 原子加分。
     *
     * <p>不要写成"查出来 +1 再 save"：并发下两个请求会读到同一个旧值，后写的覆盖先写的，
     * 分就丢了。这条 SQL 由数据库保证原子性（03 方案 §2.5）。
     */
    @Modifying
    @Query("update User u set u.totalPoints = u.totalPoints + :points where u.id = :id")
    int addPoints(@Param("id") UUID id, @Param("points") int points);

    /**
     * 对账：找出 total_points 与账本汇总不一致的用户。
     *
     * <p>为什么用 native SQL：跨表聚合 + HAVING 比较两个聚合值，JPQL 表达起来别扭，
     * 而这条 SQL 是要长期被 DBA 拿去过一遍的，写成标准 SQL 更好读。
     *
     * <p>为什么需要它：total_points 是"缓存"，point_events 才是"事实"。
     * 任何一次漏更新（异常、手工改库、并发 bug）都会让两者分叉，而分叉之后
     * 用户的分数就再也对不上了——这就是账本存在的意义。
     */
    @Query(value = """
            select u.id            as id,
                   u.total_points  as recorded,
                   coalesce(sum(e.points), 0) as computed
              from users u
              left join point_events e on e.user_id = u.id
             group by u.id, u.total_points
            having u.total_points <> coalesce(sum(e.points), 0)
            """, nativeQuery = true)
    List<PointsMismatch> findPointsMismatches();

    /** 对账修正：把缓存改成账本算出来的值。 */
    @Modifying
    @Query("update User u set u.totalPoints = :value where u.id = :id")
    int setTotalPoints(@Param("id") UUID id, @Param("value") int value);

    /** 对账结果的行。字段名与 SQL 别名对应（record 也可，但接口更贴合投影）。 */
    interface PointsMismatch {
        UUID getId();

        int getRecorded();

        long getComputed();
    }
    /** 读总分：必须是查询而不是读实体，因为 addPoints 是批量 UPDATE，会绕过一级缓存。 */
    @Query("select u.totalPoints from User u where u.id = :id")
    Optional<Integer> findTotalPointsById(@Param("id") UUID id);

    /** 含已软删：占用检查必须看到它们，否则会在唯一键上冲突。 */
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /*
     * 分页查询用 keyset（游标）而不是 offset：
     *   第 2 页 = "接着这一条之后"，而不是"跳过 20 条"。
     * 排序必须是严格全序 —— created_at 可能重复（同一个事务插入的行时间相同），
     * 所以再用 id 作为 tiebreaker，否则边界处会重复或丢行。
     */

    @Query("select u from User u where u.deletedAt is null order by u.createdAt desc, u.id desc")
    List<User> findFirstPage(Pageable pageable);

    @Query("""
            select u from User u
             where u.deletedAt is null
               and (u.createdAt < :createdAt or (u.createdAt = :createdAt and u.id < :id))
             order by u.createdAt desc, u.id desc
            """)
    List<User> findPageAfter(@Param("createdAt") OffsetDateTime createdAt,
                             @Param("id") UUID id,
                             Pageable pageable);
}
