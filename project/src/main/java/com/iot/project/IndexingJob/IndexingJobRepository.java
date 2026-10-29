package com.iot.project.IndexingJob;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.iot.project.IndexingJob.IndexingStatus.*;

// 상태 변경은 모두 "현재 상태가 from일 때만" 바꾸는 조건부 UPDATE (각각 짧은 트랜잭션 하나)
// updatedAt은 DB current_timestamp 대신 앱 시각으로 넣는다 (createdAt과 같은 시간대, DB 서버 시간대에 의존하지 않게)
public interface IndexingJobRepository extends JpaRepository<IndexingJob, Long> {

    // 처리기가 다음에 가져갈 job: 임베딩 또는 Qdrant 단계가 PENDING인 것 중 가장 오래된 것
    Optional<IndexingJob> findFirstByEmbeddingStatusOrQdrantStatusOrderByIdAsc(IndexingStatus embeddingStatus,
                                                                              IndexingStatus qdrantStatus);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update IndexingJob j set j.embeddingStatus = :to, j.updatedAt = :now
            where j.id = :id and j.embeddingStatus = :from""")
    int moveEmbedding(Long id, IndexingStatus from, IndexingStatus to, LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update IndexingJob j set j.qdrantStatus = :to, j.updatedAt = :now
            where j.id = :id and j.qdrantStatus = :from""")
    int moveQdrant(Long id, IndexingStatus from, IndexingStatus to, LocalDateTime now);

    // 임베딩 완료와 Qdrant 대기 전환을 한 트랜잭션으로 (중간에 종료돼도 절반만 반영되지 않게)
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update IndexingJob j set j.embeddingStatus = :completed, j.qdrantStatus = :pending, j.updatedAt = :now
            where j.id = :id and j.embeddingStatus = :processing""")
    int completeEmbedding(Long id, IndexingStatus processing, IndexingStatus completed, IndexingStatus pending, LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update IndexingJob j set j.embeddingStatus = :failed, j.lastError = :error, j.updatedAt = :now
            where j.id = :id and j.embeddingStatus = :processing""")
    int failEmbedding(Long id, String error, IndexingStatus processing, IndexingStatus failed, LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update IndexingJob j set j.qdrantStatus = :failed, j.lastError = :error, j.updatedAt = :now
            where j.id = :id and j.qdrantStatus = :processing""")
    int failQdrant(Long id, String error, IndexingStatus processing, IndexingStatus failed, LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update IndexingJob j set j.embeddingStatus = :to, j.updatedAt = :now where j.embeddingStatus = :from")
    int moveAllEmbedding(IndexingStatus from, IndexingStatus to, LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update IndexingJob j set j.qdrantStatus = :to, j.updatedAt = :now where j.qdrantStatus = :from")
    int moveAllQdrant(IndexingStatus from, IndexingStatus to, LocalDateTime now);

    default Optional<IndexingJob> findNextRunnable() {
        return findFirstByEmbeddingStatusOrQdrantStatusOrderByIdAsc(PENDING, PENDING);
    }

    // 선점: 바뀐 행이 1이면 내 것
    default boolean claimEmbedding(Long id) {
        return moveEmbedding(id, PENDING, PROCESSING, LocalDateTime.now()) == 1;
    }

    default boolean claimQdrant(Long id) {
        return moveQdrant(id, PENDING, PROCESSING, LocalDateTime.now()) == 1;
    }

    default void completeEmbedding(Long id) {
        completeEmbedding(id, PROCESSING, COMPLETED, PENDING, LocalDateTime.now());
    }

    default void completeQdrant(Long id) {
        moveQdrant(id, PROCESSING, COMPLETED, LocalDateTime.now());
    }

    default void failEmbedding(Long id, String error) {
        failEmbedding(id, error, PROCESSING, FAILED, LocalDateTime.now());
    }

    default void failQdrant(Long id, String error) {
        failQdrant(id, error, PROCESSING, FAILED, LocalDateTime.now());
    }

    default void supersedeEmbedding(Long id) {
        moveEmbedding(id, PROCESSING, SUPERSEDED, LocalDateTime.now());
    }

    default void supersedeQdrant(Long id) {
        moveQdrant(id, PROCESSING, SUPERSEDED, LocalDateTime.now());
    }

    // 기동 시: 이전 프로세스가 처리하다 멈춘 job을 다시 실행 대상으로 (인스턴스 1대 전제)
    default int resetProcessing() {
        return moveAllEmbedding(PROCESSING, PENDING, LocalDateTime.now()) + moveAllQdrant(PROCESSING, PENDING, LocalDateTime.now());
    }
}
