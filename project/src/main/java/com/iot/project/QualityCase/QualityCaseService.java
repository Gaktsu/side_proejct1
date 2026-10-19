package com.iot.project.QualityCase;

import com.iot.project.Equipment.Equipment;
import com.iot.project.Equipment.EquipmentNotFoundException;
import com.iot.project.Equipment.EquipmentRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
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

    // 최신 등록 사례가 먼저 오도록 id 내림차순
    @Transactional(readOnly = true)
    public List<QualityCaseResponse> findAll() {
        return qualityCaseRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(QualityCaseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public QualityCaseResponse findById(Long id) {
        return QualityCaseResponse.from(getQualityCase(id));
    }

    @Transactional
    public QualityCaseResponse update(Long id, QualityCaseUpdateRequest request) {
        QualityCase qualityCase = getQualityCase(id);
        if (qualityCase.getStatus() == CaseStatus.RESOLVED) {
            throw new ResolvedQualityCaseUpdateException(id);
        }
        validateQuantity(request.productionQuantity(), request.defectQuantity());
        Product product = getProduct(request.productId());
        ManufacturingProcess process = getProcess(request.processId());
        Equipment equipment = getEquipmentInProcess(request.equipmentId(), process);

        qualityCase.update(product, process, equipment, request.lotNo(), request.title(),
                request.problemDescription(), request.defectType(), request.productionQuantity(),
                request.defectQuantity(), request.occurredAt());
        return QualityCaseResponse.from(qualityCase);
    }

    @Transactional
    public void delete(Long id) {
        QualityCase qualityCase = getQualityCase(id);
        if (qualityCase.getStatus() == CaseStatus.RESOLVED) {
            throw new ResolvedQualityCaseDeleteException(id);
        }
        try {
            qualityCaseRepository.delete(qualityCase);
            // FK 위반을 커밋 시점이 아닌 여기서 감지하기 위해 즉시 flush
            qualityCaseRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new QualityCaseInUseException(id);
        }
    }

    // 확정 → 문서 텍스트 임베딩 → Qdrant 적재. 임베딩/적재가 실패하면 전체 롤백되어 OPEN으로 남는다
    @Transactional
    public QualityCaseResponse resolve(Long id, QualityCaseResolveRequest request) {
        QualityCase qualityCase = qualityCaseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new QualityCaseNotFoundException(id));
        if (qualityCase.getStatus() == CaseStatus.RESOLVED) {
            throw new QualityCaseAlreadyResolvedException(id);
        }
        qualityCase.resolve(request.rootCause(), request.correctiveAction(), request.resultSummary());
        // UPDATE를 Qdrant 호출 전에 실행해 DB 오류가 벡터 적재 뒤에 터지지 않게 한다
        qualityCaseRepository.flush();

        try {
            long start = System.nanoTime();
            float[] vector = embeddingClient.embedDocument(QualityCaseDocument.toText(qualityCase));
            long embedded = System.nanoTime();
            qdrantClient.upsert(qualityCase.getId(), vector);
            log.info("resolve 적재 id={}, 임베딩 {}ms, upsert {}ms", id,
                    (embedded - start) / 1_000_000, (System.nanoTime() - embedded) / 1_000_000);
        } catch (EmbeddingInputTooLongException e) {
            // 다시 시도해도 같은 결과라 503으로 바꾸지 않는다 (롤백되어 OPEN 유지)
            throw e;
        } catch (RuntimeException e) {
            throw new VectorIndexingException(id, e);
        }
        return QualityCaseResponse.from(qualityCase);
    }

    // OPEN 사례 → 쿼리 텍스트 임베딩 → Qdrant top-k → MySQL에서 RESOLVED만 남김 (Qdrant 순서 유지)
    // RESOLVED 필터: resolve의 upsert 뒤 커밋이 실패하면 OPEN 사례 벡터가 Qdrant에 남으므로
    // ponytail: 임베딩 동안 DB 커넥션을 쥠 (resolve와 같음), 동시 요청 측정에서 문제가 되면 임베딩을 트랜잭션 밖으로
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
                .filter(c -> c.getStatus() == CaseStatus.RESOLVED)
                .collect(Collectors.toMap(QualityCase::getId, c -> c));
        return points.stream()
                .filter(p -> resolved.containsKey(p.id()))
                .map(p -> new SimilarCaseResponse(p.score(), QualityCaseResponse.from(resolved.get(p.id()))))
                .toList();
    }

    private QualityCase getQualityCase(Long id) {
        return qualityCaseRepository.findById(id)
                .orElseThrow(() -> new QualityCaseNotFoundException(id));
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
