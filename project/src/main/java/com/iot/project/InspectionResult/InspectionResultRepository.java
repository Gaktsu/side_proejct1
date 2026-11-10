package com.iot.project.InspectionResult;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InspectionResultRepository extends JpaRepository<InspectionResult, Long> {

    // 최신 등록순
    List<InspectionResult> findAllByQualityCase_IdOrderByIdDesc(Long qualityCaseId);

    // 소속 사례가 논리 삭제됐으면 없는 것으로 본다
    Optional<InspectionResult> findByIdAndQualityCase_DeletedAtIsNull(Long id);
}
