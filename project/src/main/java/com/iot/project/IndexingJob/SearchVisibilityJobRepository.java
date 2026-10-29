package com.iot.project.IndexingJob;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.iot.project.IndexingJob.IndexingStatus.*;

// IndexingJobRepository와 같은 방식: 상태 변경은 "현재 상태가 from일 때만" 바꾸는 조건부 UPDATE
public interface SearchVisibilityJobRepository extends JpaRepository<SearchVisibilityJob, Long> {

    Optional<SearchVisibilityJob> findFirstByStatusOrderByIdAsc(IndexingStatus status);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update SearchVisibilityJob j set j.status = :to, j.lastError = :error, j.updatedAt = :now
            where j.id = :id and j.status = :from""")
    int move(Long id, IndexingStatus from, IndexingStatus to, String error, LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update SearchVisibilityJob j set j.status = :to, j.updatedAt = :now where j.status = :from")
    int moveAll(IndexingStatus from, IndexingStatus to, LocalDateTime now);

    default Optional<SearchVisibilityJob> findNextRunnable() {
        return findFirstByStatusOrderByIdAsc(PENDING);
    }

    // 선점: 바뀐 행이 1이면 내 것
    default boolean claim(Long id) {
        return move(id, PENDING, PROCESSING, null, LocalDateTime.now()) == 1;
    }

    default void complete(Long id) {
        move(id, PROCESSING, COMPLETED, null, LocalDateTime.now());
    }

    default void fail(Long id, String error) {
        move(id, PROCESSING, FAILED, error, LocalDateTime.now());
    }

    // 기동 시: 이전 프로세스가 처리하다 멈춘 job을 다시 실행 대상으로 (인스턴스 1대 전제)
    default int resetProcessing() {
        return moveAll(PROCESSING, PENDING, LocalDateTime.now());
    }
}
