package com.iot.project.Analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.iot.project.Analysis.AnalysisStatus.*;

// 상태 변경은 IndexingJobRepository와 같은 방식: "현재 상태가 from일 때만" 바꾸는 조건부 UPDATE
public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    // 사례당 최대 1개 (UNIQUE target_quality_case_id)
    Optional<Analysis> findByQualityCaseId(Long qualityCaseId);

    // 조회 API용: 대상 사례가 삭제됐는지 함께 봐야 하므로 사례까지 읽는다
    @Query("select a from Analysis a join fetch a.qualityCase where a.id = :id")
    Optional<Analysis> findWithCaseById(Long id);

    Optional<Analysis> findFirstByAnalysisStatusOrderByIdAsc(AnalysisStatus status);

    // 재요청 시 기존 분석 삭제. JPQL 벌크 삭제라 바로 실행된다
    // (엔티티 remove는 flush 때 INSERT 뒤로 밀려 같은 사례의 새 분석과 UNIQUE가 부딪힌다)
    @Modifying(flushAutomatically = true)
    @Query("delete from Analysis a where a.id = :id")
    void deleteNow(Long id);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update Analysis a set a.analysisStatus = :to, a.errorMessage = :error
            where a.id = :id and a.analysisStatus = :from""")
    int move(Long id, AnalysisStatus from, AnalysisStatus to, String error);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Analysis a set a.analysisStatus = :to where a.analysisStatus = :from")
    int moveAll(AnalysisStatus from, AnalysisStatus to);

    default Optional<Analysis> findNextRunnable() {
        return findFirstByAnalysisStatusOrderByIdAsc(PENDING);
    }

    // 선점: 바뀐 행이 1이면 내 것
    default boolean claim(Long id) {
        return move(id, PENDING, SEARCHING, null) == 1;
    }

    default void finish(Long id, AnalysisStatus result) {
        move(id, SEARCHING, result, null);
    }

    default void fail(Long id, String error) {
        move(id, SEARCHING, FAILED, error);
    }

    // 기동 시: 이전 프로세스가 검색하다 멈춘 분석을 처음부터 다시 (후보는 상태와 한 트랜잭션이라 일부만 남지 않음)
    default int resetSearching() {
        return moveAll(SEARCHING, PENDING);
    }
}
