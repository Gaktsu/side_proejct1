package com.iot.project.IndexingJob;

import com.iot.project.QualityCase.QualityCase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 사례의 한 버전을 Qdrant 검색에 반영하는 작업. 버전마다 한 행이고, 이전 버전 행은 끝난 상태 그대로 남는다
@Getter
@Entity
@Table(name = "indexing_job", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"quality_case_id", "source_version"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndexingJob {

    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    // QualityCase의 FK
    @JoinColumn(name = "quality_case_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private QualityCase qualityCase;

    // 이 job이 반영하는 사례 내용의 번호 (사례가 이후 또 수정돼도 그대로)
    @Column(nullable = false)
    private Integer sourceVersion;

    // 임베딩 단계 상태
    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private IndexingStatus embeddingStatus;

    // Qdrant 저장 단계 상태
    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private IndexingStatus qdrantStatus;

    // 마지막 실패 메시지
    @Column(columnDefinition = "TEXT")
    private String lastError;

    // 등록일
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 상태가 마지막으로 바뀐 시각 (오래 멈춘 단계를 찾는 단서)
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 해결·수정 트랜잭션 안에서 사례의 현재 버전으로 만든다
    public IndexingJob(QualityCase qualityCase) {
        this.qualityCase = qualityCase;
        this.sourceVersion = qualityCase.getSourceVersion();
        this.embeddingStatus = IndexingStatus.PENDING;
        this.qdrantStatus = IndexingStatus.WAITING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }
}
