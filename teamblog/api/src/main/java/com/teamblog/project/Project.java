package com.teamblog.project;

import com.teamblog.common.UuidUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 成果（项目 / 证书 / 奖项），映射 {@code projects} 表。
 *
 * <p>{@code updated_at} 映射成只读：数据库上有触发器 {@code trg_projects_updated} 负责维护。
 * 如果 Hibernate 也去写它，两边会打架，而且"实体改了什么"就说不清了——只留一个真相源。
 */
@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ProjectType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProjectStatus status;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(name = "github_url", length = 300)
    private String githubUrl;

    @Column(name = "content_md")
    private String contentMd;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "removed_at")
    private OffsetDateTime removedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 由数据库触发器维护，实体只读（见类注释）。 */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public static Project create(UUID ownerId, ProjectType type, String title, String description,
                                 String githubUrl, String contentMd, String imageUrl) {
        Project project = new Project();
        project.id = UuidUtils.v7();
        project.ownerId = Objects.requireNonNull(ownerId, "ownerId 不能为空");
        project.type = Objects.requireNonNull(type, "type 不能为空");
        project.status = ProjectStatus.ACTIVE;
        project.title = Objects.requireNonNull(title, "title 不能为空");
        project.description = Objects.requireNonNull(description, "description 不能为空");
        project.githubUrl = githubUrl;
        project.contentMd = contentMd;
        project.imageUrl = imageUrl;
        return project;
    }

    /** 类型不可改：改了类型，必填字段的规则就跟着变，历史数据会变得没有意义。 */
    public void updateDetails(String title, String description,
                              String githubUrl, String contentMd, String imageUrl) {
        this.title = Objects.requireNonNull(title, "title 不能为空");
        this.description = Objects.requireNonNull(description, "description 不能为空");
        this.githubUrl = githubUrl;
        this.contentMd = contentMd;
        this.imageUrl = imageUrl;
    }

    /** 软下架：保留数据，只是不再对外可见。 */
    public void remove(OffsetDateTime now) {
        if (this.status != ProjectStatus.REMOVED) {
            this.status = ProjectStatus.REMOVED;
            this.removedAt = Objects.requireNonNull(now, "时间不能为空");
        }
    }

    public boolean isRemoved() {
        return status == ProjectStatus.REMOVED;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }
}
