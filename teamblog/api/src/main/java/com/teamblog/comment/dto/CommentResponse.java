package com.teamblog.comment.dto;

import com.teamblog.comment.Comment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 一条评论（带它的回复）。
 *
 * <p>软删的评论有两种表示：
 * <ul>
 *   <li>有回复 → 保留节点，{@code content = null} + {@code deleted = true}，
 *       前端渲染"该评论已删除"，回复仍挂在下面</li>
 *   <li>没有回复 → 整条不返回（没有信息量的占位）</li>
 * </ul>
 */
public record CommentResponse(
        UUID id,
        String username,
        String content,
        boolean deleted,
        OffsetDateTime createdAt,
        List<CommentResponse> replies
) {

    public static CommentResponse of(Comment comment, String username, List<CommentResponse> replies) {
        return new CommentResponse(
                comment.getId(),
                username,
                comment.isDeleted() ? null : comment.getContent(),
                comment.isDeleted(),
                comment.getCreatedAt(),
                replies);
    }
}
