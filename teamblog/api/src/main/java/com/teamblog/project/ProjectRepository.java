package com.teamblog.project;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 成果仓储。
 *
 * <p>与用户列表同一套 keyset 分页（A-08）：{@code created_at} 不唯一，必须用 id 兜底。
 * 见 {@code CursorUtils} 的说明。
 */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByIdAndStatus(UUID id, ProjectStatus status);

    @Query("""
            select p from Project p
             where p.status = :status
             order by p.createdAt desc, p.id desc
            """)
    List<Project> findFirstPage(@Param("status") ProjectStatus status, Pageable pageable);

    @Query("""
            select p from Project p
             where p.status = :status
               and (p.createdAt < :createdAt or (p.createdAt = :createdAt and p.id < :id))
             order by p.createdAt desc, p.id desc
            """)
    List<Project> findPageAfter(@Param("status") ProjectStatus status,
                                @Param("createdAt") OffsetDateTime createdAt,
                                @Param("id") UUID id,
                                Pageable pageable);
}
