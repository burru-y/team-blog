package com.teamblog.project.dto;

import com.teamblog.project.Project;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 列表里的一条成果。
 *
 * <p>不带 {@code contentMd}：正文可能几 KB，列表一页 20 条就是几十 KB 的无用传输。
 * 详情页再取。
 */
public record ProjectCardResponse(
        UUID id,
        String type,
        String title,
        String description,
        String imageUrl,
        String githubUrl,
        String ownerUsername,
        OffsetDateTime createdAt
) {

    public static ProjectCardResponse from(Project project, String ownerUsername) {
        return new ProjectCardResponse(
                project.getId(),
                project.getType().name(),
                project.getTitle(),
                project.getDescription(),
                project.getImageUrl(),
                project.getGithubUrl(),
                ownerUsername,
                project.getCreatedAt());
    }
}
