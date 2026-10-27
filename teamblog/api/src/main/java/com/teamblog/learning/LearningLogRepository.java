package com.teamblog.learning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LearningLogRepository extends JpaRepository<LearningLog, UUID> {

    /** 计分上限用：这个人在某个业务日期已经发了几篇。 */
    long countByUserIdAndLogDate(UUID userId, LocalDate logDate);

    List<LearningLog> findByUserIdOrderByLogDateDescCreatedAtDesc(UUID userId);

    /** 全站最新笔记（首页展示用）：按发布日期倒序，同一天按创建时间倒序。 */
    List<LearningLog> findAllByOrderByLogDateDescCreatedAtDesc(org.springframework.data.domain.Pageable pageable);

    /**
     * 笔记列表页：标题/正文关键词 + 标签 + 作者，三者都可为空。
     *
     * <p>用一条 @Query 而不是拼 Specification：条件只有三个，JPQL 更好读，
     * 也不必为动态查询引入 criteria API。
     *
     * <p>关键词用 like 而非全文检索：当前数据量在百级，上 pg_trgm 或 tsvector 是过度设计。
     */
    @Query("""
            select l from LearningLog l
            where (:keyword is null
                   or lower(l.title) like :keyword
                   or lower(l.contentMd) like :keyword)
              and (:tag is null or l.tags like :tag)
              and (:userId is null or l.userId = :userId)
            order by l.logDate desc, l.createdAt desc
            """)
    List<LearningLog> search(@Param("keyword") String keyword,
                             @Param("tag") String tag,
                             @Param("userId") UUID userId);
}
