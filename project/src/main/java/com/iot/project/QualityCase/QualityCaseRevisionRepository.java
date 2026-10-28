package com.iot.project.QualityCase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QualityCaseRevisionRepository extends JpaRepository<QualityCaseRevision, Long> {

    List<QualityCaseRevision> findByQualityCaseIdOrderByVersionDesc(Long qualityCaseId);
}
