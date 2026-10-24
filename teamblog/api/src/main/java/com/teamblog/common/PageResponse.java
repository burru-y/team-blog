package com.teamblog.common;

import java.util.List;

/**
 * 统一的分页信封（A-08）：{@code {items, nextCursor}}。
 *
 * <p>为什么用 cursor 而不是 offset：
 * <pre>
 * offset 分页：第 2 页 = "跳过 20 条"。如果在这期间有人新增了一条，
 *             第 2 页的第一条就会和第 1 页的最后一条重复；有人删除则会漏掉一条。
 * cursor 分页：第 2 页 = "接着这一条之后"。数据怎么变，都不会重复或漏。
 * </pre>
 * 成员列表、成果流、评论都是"边看边增长"的场景，重复和漏行比"翻不到最后一页"更烦人。
 *
 * <p>{@code nextCursor == null} 表示没有下一页。
 */
public record PageResponse<T>(List<T> items, String nextCursor) {

    public static <T> PageResponse<T> of(List<T> items, String nextCursor) {
        return new PageResponse<>(items, nextCursor);
    }
}
