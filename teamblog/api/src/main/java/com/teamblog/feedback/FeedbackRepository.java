package com.teamblog.feedback;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    List<Feedback> findByStatusOrderByCreatedAtDesc(FeedbackStatus status, Pageable pageable);

    List<Feedback> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<Feedback> findById(UUID id);

    long countByStatus(FeedbackStatus status);
}
