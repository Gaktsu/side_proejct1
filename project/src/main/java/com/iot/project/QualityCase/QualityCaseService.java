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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QualityCaseService {

    private final QualityCaseRepository qualityCaseRepository;
    private final ProductRepository productRepository;
    private final ManufacturingProcessRepository manufacturingProcessRepository;
    private final EquipmentRepository equipmentRepository;

    @Transactional
    public QualityCaseResponse create(QualityCaseCreateRequest request) {
        if (request.defectQuantity() > request.productionQuantity()) {
            throw new InvalidDefectQuantityException(request.productionQuantity(), request.defectQuantity());
        }
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));
        ManufacturingProcess process = manufacturingProcessRepository.findById(request.processId())
                .orElseThrow(() -> new ManufacturingProcessNotFoundException(request.processId()));
        Equipment equipment = null;
        if (request.equipmentId() != null) {
            equipment = equipmentRepository.findById(request.equipmentId())
                    .orElseThrow(() -> new EquipmentNotFoundException(request.equipmentId()));
            if (!equipment.getManufacturingProcess().getId().equals(process.getId())) {
                throw new EquipmentProcessMismatchException(equipment.getId(), process.getId());
            }
        }

        QualityCase qualityCase = new QualityCase(generateCaseCode(), product, process, equipment,
                request.lotNo(), request.title(), request.problemDescription(), request.defectType(),
                request.productionQuantity(), request.defectQuantity(), request.occurredAt());
        return QualityCaseResponse.from(qualityCaseRepository.save(qualityCase));
    }

    @Transactional
    public void delete(Long id) {
        QualityCase qualityCase = qualityCaseRepository.findById(id)
                .orElseThrow(() -> new QualityCaseNotFoundException(id));
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

    // 순번이 아닌 랜덤값이라 동시 등록에도 충돌하지 않는다 (가상 사례 생성기 도입 시 재검토)
    private static String generateCaseCode() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String random = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "QC-" + date + "-" + random;
    }
}
