package com.iot.project.Equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    boolean existsByEquipmentCode(String equipmentCode);

    List<Equipment> findByManufacturingProcessIdOrderByIdAsc(Long processId);
}
