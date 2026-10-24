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

    /** 管理端：不按状态过滤，已下架的也要能看到（数据还在，只是前台不展示）。 */
    List<Project> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Project> findByStatusOrderByCreatedAtDesc(ProjectStatus status, Pageable pageable);

    @Query("""
            select p from Project p
             where p.status = :status
             order by p.createdAt desc, p.id desc
            """)
    List<Project> findFirstPage(@Param("status") ProjectStatus status, Pageable pageable);

    /**
     * 个人页用：按作者过滤。单独的查询而不是给上面两个加可选参数，
     * 因为 `and (:ownerId is null or p.ownerId = :ownerId)` 这种写法会让
     * PostgreSQL 无法用上索引（参数为空时走全表扫描）。
     */
    @Query("""
            select p from Project p
             where p.status = :status and p.ownerId = :ownerId
             order by p.createdAt desc, p.id desc
            """)
    List<Project> findFirstPageByOwner(@Param("status") ProjectStatus status,
                                      @Param("ownerId") UUID ownerId,
                                      Pageable pageable);

    @Query("""
            select p from Project p
             where p.status = :status and p.ownerId = :ownerId
               and (p.createdAt < :createdAt or (p.createdAt = :createdAt and p.id < :id))
             order by p.createdAt desc, p.id desc
            """)
    List<Project> findPageAfterByOwner(@Param("status") ProjectStatus status,
                                       @Param("ownerId") UUID ownerId,
                                       @Param("createdAt") OffsetDateTime createdAt,
                                       @Param("id") UUID id,
                                       Pageable pageable);

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
