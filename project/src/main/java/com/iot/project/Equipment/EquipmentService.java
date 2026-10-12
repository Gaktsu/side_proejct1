package com.iot.project.Equipment;

import com.iot.project.ManufacturingProcess.ManufacturingProcess;
import com.iot.project.ManufacturingProcess.ManufacturingProcessNotFoundException;
import com.iot.project.ManufacturingProcess.ManufacturingProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final ManufacturingProcessRepository manufacturingProcessRepository;

    @Transactional
    public EquipmentResponse create(Long processId, EquipmentCreateRequest request) {
        ManufacturingProcess process = manufacturingProcessRepository.findById(processId)
                .orElseThrow(() -> new ManufacturingProcessNotFoundException(processId));
        if (equipmentRepository.existsByEquipmentCode(request.equipmentCode())) {
            throw new DuplicateEquipmentCodeException(request.equipmentCode());
        }
        Equipment equipment = new Equipment(process, request.equipmentCode(), request.name(), request.description());
        try {
            // 동시 요청이 exists 검사를 함께 통과한 경우 DB UNIQUE 제약이 막는다
            return EquipmentResponse.from(equipmentRepository.saveAndFlush(equipment));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEquipmentCodeException(request.equipmentCode());
        }
    }

    @Transactional(readOnly = true)
    public List<EquipmentResponse> findAllByProcess(Long processId) {
        if (!manufacturingProcessRepository.existsById(processId)) {
            throw new ManufacturingProcessNotFoundException(processId);
        }
        return equipmentRepository.findByManufacturingProcessIdOrderByIdAsc(processId).stream()
                .map(EquipmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EquipmentResponse findById(Long id) {
        return EquipmentResponse.from(getEquipment(id));
    }

    @Transactional
    public EquipmentResponse update(Long id, EquipmentUpdateRequest request) {
        Equipment equipment = getEquipment(id);
        equipment.update(request.name(), request.description());
        return EquipmentResponse.from(equipment);
    }

    @Transactional
    public void delete(Long id) {
        Equipment equipment = getEquipment(id);
        try {
            equipmentRepository.delete(equipment);
            // FK 위반을 커밋 시점이 아닌 여기서 감지하기 위해 즉시 flush
            equipmentRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new EquipmentInUseException(id);
        }
    }

    private Equipment getEquipment(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new EquipmentNotFoundException(id));
    }
}
