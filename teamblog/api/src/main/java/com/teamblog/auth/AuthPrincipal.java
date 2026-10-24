package com.teamblog.auth;

import com.teamblog.user.Role;

import java.util.UUID;

/**
 * 已认证用户在请求内的身份（SecurityContext 的 principal）。
 *
 * <p>为什么不用实体本身当 principal：实体带着 passwordHash、tokenVersion 这些字段，
 * 一旦有人把它打进日志或序列化出去就出事；principal 只放"这次请求需要知道的三件事"。
 *
 * <p>为什么带 username：日志与审计要能直接看出是谁，不用再查库（S-02：角色仍然以
 * 数据库为准，这里的 role 只是本次请求的快照，写操作必须重新校验）。
 */
public record AuthPrincipal(UUID id, String username, Role role) {
}
