package com.teamblog.project;

/**
 * 成果类型。对应 {@code projects.type} 的 CHECK 约束。
 *
 * <p>三种类型的必填字段不同（见 V1 的 {@code ck_projects_required}）：
 * <ul>
 *   <li>{@code PROJECT}：必须有仓库/链接 + 正文</li>
 *   <li>{@code CERTIFICATE} / {@code AWARD}：至少要有图或链接</li>
 * </ul>
 * 这种"按类型分支的校验"是业务规则，写在 Service 里；数据库的 CHECK 是最后一道防线。
 */
public enum ProjectType {

    PROJECT,
    CERTIFICATE,
    AWARD
}
