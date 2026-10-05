package com.iot.project.ManufacturingProcess;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ManufacturingProcessRepository extends JpaRepository<ManufacturingProcess, Long> {
    boolean existsByProcessCode(String processCode);
}
