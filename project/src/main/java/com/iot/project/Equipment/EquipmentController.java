package com.iot.project.Equipment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    @PostMapping("/processes/{processId}/equipments")
    @ResponseStatus(HttpStatus.CREATED)
    public EquipmentResponse create(@PathVariable Long processId, @Valid @RequestBody EquipmentCreateRequest request) {
        return equipmentService.create(processId, request);
    }

    @DeleteMapping("/equipments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        equipmentService.delete(id);
    }
}
