package com.teamblog.project;

/**
 * 成果状态。下架不是删除（D-13 的软删思路）。
 *
 * <p>为什么保留数据：下架的成果可能只是"暂时不公开"，管理端还要能看到、能恢复；
 * 而且用户误删时还有救。真正删除属于一次不可逆操作，不该由一个按钮完成。
 */
public enum ProjectStatus {

    ACTIVE,
    REMOVED
}
