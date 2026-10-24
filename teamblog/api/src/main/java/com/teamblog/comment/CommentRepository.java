package com.teamblog.comment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** 一次取回目标下所有评论（含已删），再在内存里拼树——两级结构量很小。 */
    List<Comment> findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CommentTargetType targetType, UUID targetId);

    long countByTargetTypeAndTargetIdAndDeletedAtIsNull(CommentTargetType targetType, UUID targetId);
}
