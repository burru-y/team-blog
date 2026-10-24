package com.teamblog.points;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PointEventRepository extends JpaRepository<PointEvent, UUID> {

    List<PointEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndKind(UUID userId, PointEventKind kind);

    boolean existsByUserIdAndKindAndRefId(UUID userId, PointEventKind kind, String refId);
}
