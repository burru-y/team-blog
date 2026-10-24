package com.teamblog.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** 含已软删：占用检查必须看到它们，否则会在唯一键上冲突。 */
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
