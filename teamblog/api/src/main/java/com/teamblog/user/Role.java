package com.teamblog.user;

/**
 * 账号角色。对应 {@code users.role} 的 {@code varchar(10)} + CHECK（D-10）。
 *
 * <p>只有两个值：访客没有账号，不占角色位。权限判定以数据库里的这一列为准（S-02），
 * token 里的 role 只用于展示与日志。
 */
public enum Role {

    ADMIN,
    MEMBER
}
