package com.teamblog.learning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LearningLogRepository extends JpaRepository<LearningLog, UUID> {

    /** 计分上限用：这个人在某个业务日期已经发了几篇。 */
    long countByUserIdAndLogDate(UUID userId, LocalDate logDate);

    List<LearningLog> findByUserIdOrderByLogDateDescCreatedAtDesc(UUID userId);
}
