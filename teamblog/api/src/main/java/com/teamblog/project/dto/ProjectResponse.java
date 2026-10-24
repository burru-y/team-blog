package com.teamblog.project.dto;

import com.teamblog.project.Project;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 详情页用的完整表示。 */
public record ProjectResponse(
        UUID id,
        String type,
        String title,
        String description,
        String githubUrl,
        String contentMd,
        String imageUrl,
        String ownerUsername,
        OffsetDateTime createdAt
) {

    public static ProjectResponse from(Project project, String ownerUsername) {
        return new ProjectResponse(
                project.getId(),
                project.getType().name(),
                project.getTitle(),
                project.getDescription(),
                project.getGithubUrl(),
                project.getContentMd(),
                project.getImageUrl(),
                ownerUsername,
                project.getCreatedAt());
    }
}
