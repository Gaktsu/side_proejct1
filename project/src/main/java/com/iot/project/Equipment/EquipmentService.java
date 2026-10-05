package com.iot.project.Equipment;

import com.iot.project.ManufacturingProcess.ManufacturingProcess;
import com.iot.project.ManufacturingProcess.ManufacturingProcessNotFoundException;
import com.iot.project.ManufacturingProcess.ManufacturingProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return EquipmentResponse.from(equipmentRepository.save(equipment));
    }

    @Transactional
    public void delete(Long id) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new EquipmentNotFoundException(id));
        try {
            equipmentRepository.delete(equipment);
            // FK 위반을 커밋 시점이 아닌 여기서 감지하기 위해 즉시 flush
            equipmentRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new EquipmentInUseException(id);
        }
    }
}
