package com.iot.project.QualityCase;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface QualityCaseRepository extends JpaRepository<QualityCase, Long> {

    // SELECT ... FOR UPDATE: 같은 사례의 resolve 동시 요청을 한 줄로 세운다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QualityCase q where q.id = :id")
    Optional<QualityCase> findByIdForUpdate(Long id);
}
