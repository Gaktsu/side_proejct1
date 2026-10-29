package com.iot.project.IndexingJob;

import com.iot.project.QualityCase.QualityCase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 사례의 Qdrant searchable을 MySQL 현재 상태에 맞추라는 할 일 (해결 취소·삭제·복구·인덱싱 완료 때 추가)
// 목표 값을 저장하지 않는다: SearchVisibilityWorker가 실행 시점에 MySQL을 읽어 정하므로 순서·중복과 무관하게 마지막 실행이 맞춘다
@Getter
@Entity
@Table(name = "search_visibility_job")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchVisibilityJob {

    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    // QualityCase의 FK
    @JoinColumn(name = "quality_case_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private QualityCase qualityCase;

    // PENDING / PROCESSING / COMPLETED / FAILED
    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private IndexingStatus status;

    // 마지막 실패 메시지
    @Column(columnDefinition = "TEXT")
    private String lastError;

    // 등록일
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 상태가 마지막으로 바뀐 시각
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public SearchVisibilityJob(QualityCase qualityCase) {
        this.qualityCase = qualityCase;
        this.status = IndexingStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }
}
