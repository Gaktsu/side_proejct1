package com.iot.project.QualityCase;

import com.iot.project.Equipment.Equipment;
import com.iot.project.Equipment.EquipmentNotFoundException;
import com.iot.project.Equipment.EquipmentRepository;
import com.iot.project.IndexingJob.IndexingJob;
import com.iot.project.IndexingJob.IndexingJobRepository;
import com.iot.project.IndexingJob.SearchVisibilityJob;
import com.iot.project.IndexingJob.SearchVisibilityJobRepository;
import com.iot.project.ManufacturingProcess.ManufacturingProcess;
import com.iot.project.ManufacturingProcess.ManufacturingProcessNotFoundException;
import com.iot.project.ManufacturingProcess.ManufacturingProcessRepository;
import com.iot.project.Product.Product;
import com.iot.project.Product.ProductNotFoundException;
import com.iot.project.Product.ProductRepository;
import com.iot.project.embedding.EmbeddingClient;
import com.iot.project.embedding.EmbeddingInputTooLongException;
import com.iot.project.qdrant.QdrantClient;
import com.iot.project.qdrant.ScoredPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualityCaseService {

    // 부정 조건을 못 가리는 임베딩 대신 JEV/LLM이 고를 수 있게 넉넉히 가져온다
    static final int SIMILAR_TOP_K = 10;

    private final QualityCaseRepository qualityCaseRepository;
    private final ProductRepository productRepository;
    private final ManufacturingProcessRepository manufacturingProcessRepository;
    private final EquipmentRepository equipmentRepository;
    private final IndexingJobRepository indexingJobRepository;
    private final SearchVisibilityJobRepository visibilityJobRepository;
    private final QualityCaseRevisionRepository revisionRepository;
    private final EmbeddingClient embeddingClient;
    private final QdrantClient qdrantClient;

    @Transactional
    public QualityCaseResponse create(QualityCaseCreateRequest request) {
        validateQuantity(request.productionQuantity(), request.defectQuantity());
        Product product = getProduct(request.productId());
        ManufacturingProcess process = getProcess(request.processId());
        Equipment equipment = getEquipmentInProcess(request.equipmentId(), process);

        QualityCase qualityCase = new QualityCase(generateCaseCode(), product, process, equipment,
                request.lotNo(), request.title(), request.problemDescription(), request.defectType(),
                request.productionQuantity(), request.defectQuantity(), request.occurredAt());
        return QualityCaseResponse.from(qualityCaseRepository.save(qualityCase));
    }

    // 최신 등록 사례가 먼저 오도록 id 내림차순, 현재 버전의 검색 반영 상태 포함
    @Transactional(readOnly = true)
    public List<QualityCaseResponse> findAll() {
        return qualityCaseRepository.findAllWithCurrentJob().stream()
                .map(row -> QualityCaseResponse.from((QualityCase) row[0], (IndexingJob) row[1])).toList();
    }

    @Transactional(readOnly = true)
    public QualityCaseResponse findById(Long id) {
        return qualityCaseRepository.findWithCurrentJobById(id).stream()
                .map(row -> QualityCaseResponse.from((QualityCase) row[0], (IndexingJob) row[1]))
                .findFirst()
                .orElseThrow(() -> new QualityCaseNotFoundException(id));
    }

    // OPEN: 등록 정보만 교체. RESOLVED: 직전 값을 이력에 남기고 원인/조치/결과까지 교체 → 새 버전 + 새 인덱싱 job
    // 이전 버전 job이 진행 중이어도 막지 않는다 (처리기가 버전을 비교해 옛 job을 건너뜀)
    @Transactional
    public QualityCaseResponse update(Long id, QualityCaseUpdateRequest request) {
        QualityCase qualityCase = getQualityCaseForUpdate(id);
        validateQuantity(request.productionQuantity(), request.defectQuantity());
        Product product = getProduct(request.productId());
        ManufacturingProcess process = getProcess(request.processId());
        Equipment equipment = getEquipmentInProcess(request.equipmentId(), process);

        if (qualityCase.getStatus() == CaseStatus.OPEN) {
            if (request.rootCause() != null || request.correctiveAction() != null || request.resultSummary() != null) {
                throw new InvalidResolutionFieldsException("OPEN 품질사례에는 원인·조치·결과를 넣을 수 없습니다. 해결 처리(/resolve)를 사용하세요. id=" + id);
            }
            qualityCase.update(product, process, equipment, request.lotNo(), request.title(),
                    request.problemDescription(), request.defectType(), request.productionQuantity(),
                    request.defectQuantity(), request.occurredAt());
            return QualityCaseResponse.from(qualityCase);
        }

        if (isBlank(request.rootCause()) || isBlank(request.correctiveAction())) {
            throw new InvalidResolutionFieldsException("해결된 품질사례를 수정할 때는 원인과 조치가 필요합니다. id=" + id);
        }
        revisionRepository.save(new QualityCaseRevision(qualityCase));
        qualityCase.revise(product, process, equipment, request.lotNo(), request.title(),
                request.problemDescription(), request.defectType(), request.productionQuantity(),
                request.defectQuantity(), request.occurredAt(),
                request.rootCause(), request.correctiveAction(), request.resultSummary());
        checkDocumentLength(qualityCase);
        return QualityCaseResponse.from(qualityCase, indexingJobRepository.save(new IndexingJob(qualityCase)));
    }

    // 버전 내림차순. 보여주기만 하고 검색에는 쓰지 않는다
    @Transactional(readOnly = true)
    public List<QualityCaseRevisionResponse> findRevisions(Long id) {
        if (!qualityCaseRepository.existsByIdAndDeletedAtIsNull(id)) {
            throw new QualityCaseNotFoundException(id);
        }
        return revisionRepository.findByQualityCaseIdOrderByVersionDesc(id).stream()
                .map(QualityCaseRevisionResponse::from).toList();
    }

    // 논리 삭제: OPEN·RESOLVED 모두, 상태·버전은 그대로. 검색 제외는 커밋 뒤 SearchVisibilityWorker가 Qdrant에 반영
    // OPEN이어도 노출 job을 남긴다 (해결 취소 전 RESOLVED 시절 포인트가 남아 있을 수 있음)
    @Transactional
    public void delete(Long id) {
        QualityCase qualityCase = getQualityCaseForUpdate(id);
        qualityCase.delete();
        visibilityJobRepository.save(new SearchVisibilityJob(qualityCase));
    }

    // 삭제 전 상태 그대로 되돌린다. RESOLVED면 Qdrant 벡터 버전이 현재 버전과 같을 때만 다시 검색된다
    @Transactional
    public QualityCaseResponse restore(Long id) {
        QualityCase qualityCase = qualityCaseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new QualityCaseNotFoundException(id));
        if (!qualityCase.isDeleted()) {
            throw new QualityCaseNotDeletedException(id);
        }
        qualityCase.restore();
        visibilityJobRepository.save(new SearchVisibilityJob(qualityCase));
        return findById(id);
    }

    // 해결 취소: 직전 값을 이력에 남기고 원인/조치/결과를 비워 OPEN + 새 버전 (진행 중인 인덱싱 job은 처리기가 SUPERSEDED)
    @Transactional
    public QualityCaseResponse reopen(Long id) {
        QualityCase qualityCase = getQualityCaseForUpdate(id);
        if (qualityCase.getStatus() != CaseStatus.RESOLVED) {
            throw new QualityCaseNotResolvedException(id);
        }
        revisionRepository.save(new QualityCaseRevision(qualityCase));
        qualityCase.reopen();
        visibilityJobRepository.save(new SearchVisibilityJob(qualityCase));
        return QualityCaseResponse.from(qualityCase);
    }

    // 확정 + 인덱싱 job(PENDING/WAITING)을 한 트랜잭션으로 기록하고 바로 응답한다
    // 임베딩·Qdrant는 커밋 뒤 IndexingWorker가 트랜잭션 밖에서 처리 (해결 완료와 검색 반영 완료는 별개)
    @Transactional
    public QualityCaseResponse resolve(Long id, QualityCaseResolveRequest request) {
        QualityCase qualityCase = getQualityCaseForUpdate(id);
        if (qualityCase.getStatus() == CaseStatus.RESOLVED) {
            throw new QualityCaseAlreadyResolvedException(id);
        }
        qualityCase.resolve(request.rootCause(), request.correctiveAction(), request.resultSummary());
        checkDocumentLength(qualityCase);
        return QualityCaseResponse.from(qualityCase, indexingJobRepository.save(new IndexingJob(qualityCase)));
    }

    // 비동기 처리 중에 토큰 초과가 나면 해결됐는데 영영 검색되지 않으므로, 커밋 전에 동기로 거절한다 (롤백되어 변경 없음)
    private void checkDocumentLength(QualityCase qualityCase) {
        try {
            embeddingClient.checkDocumentLength(QualityCaseDocument.toText(qualityCase));
        } catch (EmbeddingInputTooLongException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new TokenCheckUnavailableException(qualityCase.getId(), e);
        }
    }

    // 디버그·성능 측정용: Analysis와 별개로 검색 단계(임베딩 → Qdrant → MySQL 대조)만 확인한다
    // OPEN 사례 → 쿼리 텍스트 임베딩 → Qdrant top-k(searchable만) → MySQL에서 RESOLVED·삭제 안 됨·버전 같음만 (Qdrant 순서 유지)
    // MySQL 대조: 처리기가 옛 버전 벡터를 남겼거나, 취소·삭제의 searchable=false가 아직 반영되지 않은 경우를 거른다
    // ponytail: 임베딩 동안 DB 커넥션을 쥠, 동시 요청 측정에서 문제가 되면 임베딩을 트랜잭션 밖으로
    @Transactional(readOnly = true)
    public List<SimilarCaseResponse> findSimilar(Long id) {
        QualityCase qualityCase = getQualityCase(id);
        if (qualityCase.getStatus() == CaseStatus.RESOLVED) {
            throw new ResolvedQualityCaseSearchException(id);
        }

        List<ScoredPoint> points;
        try {
            long start = System.nanoTime();
            float[] vector = embeddingClient.embedQuery(QualityCaseDocument.toQueryText(qualityCase));
            long embedded = System.nanoTime();
            points = qdrantClient.search(vector, SIMILAR_TOP_K);
            log.info("유사 사례 검색 id={}, 임베딩 {}ms, search {}ms, 후보 {}건", id,
                    (embedded - start) / 1_000_000, (System.nanoTime() - embedded) / 1_000_000, points.size());
        } catch (EmbeddingInputTooLongException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new VectorSearchException(id, e);
        }

        Map<Long, QualityCase> resolved = qualityCaseRepository.findAllById(points.stream().map(ScoredPoint::id).toList())
                .stream()
                .filter(c -> c.getStatus() == CaseStatus.RESOLVED && !c.isDeleted())
                .collect(Collectors.toMap(QualityCase::getId, c -> c));
        return points.stream()
                .filter(p -> resolved.containsKey(p.id())
                        && p.payload().sourceVersion() == resolved.get(p.id()).getSourceVersion())
                .map(p -> new SimilarCaseResponse(p.score(), QualityCaseResponse.from(resolved.get(p.id()))))
                .toList();
    }

    private QualityCase getQualityCase(Long id) {
        return qualityCaseRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new QualityCaseNotFoundException(id));
    }

    private QualityCase getQualityCaseForUpdate(Long id) {
        return qualityCaseRepository.findByIdForUpdate(id)
                .filter(qualityCase -> !qualityCase.isDeleted())
                .orElseThrow(() -> new QualityCaseNotFoundException(id));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void validateQuantity(Integer productionQuantity, Integer defectQuantity) {
        if (defectQuantity > productionQuantity) {
            throw new InvalidDefectQuantityException(productionQuantity, defectQuantity);
        }
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private ManufacturingProcess getProcess(Long processId) {
        return manufacturingProcessRepository.findById(processId)
                .orElseThrow(() -> new ManufacturingProcessNotFoundException(processId));
    }

    // 설비는 선택 항목. 지정했다면 사례의 공정에 속해야 한다
    private Equipment getEquipmentInProcess(Long equipmentId, ManufacturingProcess process) {
        if (equipmentId == null) {
            return null;
        }
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new EquipmentNotFoundException(equipmentId));
        if (!equipment.getManufacturingProcess().getId().equals(process.getId())) {
            throw new EquipmentProcessMismatchException(equipment.getId(), process.getId());
        }
        return equipment;
    }

    // 순번이 아닌 랜덤값이라 동시 등록에도 충돌하지 않는다 (가상 사례 생성기 도입 시 재검토)
    private static String generateCaseCode() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String random = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "QC-" + date + "-" + random;
    }
}
