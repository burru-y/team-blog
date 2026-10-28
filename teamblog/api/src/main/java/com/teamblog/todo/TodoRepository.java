package com.teamblog.todo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TodoRepository extends JpaRepository<Todo, UUID> {

    /**
     * 某人的全部待办：未完成在前，组内按录入顺序。
     *
     * <p>不用"按完成时间倒序"：勾掉一条就跳到最底下，看的人会以为它消失了。
     */
    List<Todo> findByUserIdOrderByDoneAscPositionAscCreatedAtAsc(UUID userId);

    long countByUserIdAndDoneFalse(UUID userId);

    /** 新条目的位置：取当前最大值 +1，避免每次重排。 */
    default int nextPosition(UUID userId) {
        return (int) findByUserIdOrderByDoneAscPositionAscCreatedAtAsc(userId).stream()
                .mapToInt(Todo::getPosition)
                .max()
                .orElse(-1) + 1;
    }
}
