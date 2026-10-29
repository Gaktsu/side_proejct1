package com.iot.project.QualityCase;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface QualityCaseRepository extends JpaRepository<QualityCase, Long> {

    // 삭제된 사례는 restore 외의 API에서 없는 것으로 다룬다 (404)
    Optional<QualityCase> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByIdAndDeletedAtIsNull(Long id);

    // SELECT ... FOR UPDATE: 같은 사례의 resolve·수정·취소·삭제·복구 동시 요청을 한 줄로 세운다 (삭제된 사례도 읽음)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QualityCase q where q.id = :id")
    Optional<QualityCase> findByIdForUpdate(Long id);

    // [사례, 현재 버전 job 또는 null]. 현재 버전 job은 UNIQUE(사례, 버전)로 최대 1개라 행이 늘지 않는다 (N+1 없음)
    @Query("""
            select q, j from QualityCase q
            left join IndexingJob j on j.qualityCase = q and j.sourceVersion = q.sourceVersion
            where q.deletedAt is null
            order by q.id desc""")
    List<Object[]> findAllWithCurrentJob();

    @Query("""
            select q, j from QualityCase q
            left join IndexingJob j on j.qualityCase = q and j.sourceVersion = q.sourceVersion
            where q.id = :id and q.deletedAt is null""")
    List<Object[]> findWithCurrentJobById(Long id);

    // 인덱싱 처리기용(삭제된 사례도 읽음): 트랜잭션 밖에서 문서 텍스트를 만들 수 있게 연관 엔티티까지 한 번에 읽는다
    @Query("""
            select q from QualityCase q
            join fetch q.product join fetch q.manufacturingProcess left join fetch q.equipment
            where q.id = :id""")
    Optional<QualityCase> findWithRefsById(Long id);

    @Query("select q.sourceVersion from QualityCase q where q.id = :id")
    int findSourceVersionById(Long id);
}
