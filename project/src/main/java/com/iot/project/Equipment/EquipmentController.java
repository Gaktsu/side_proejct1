package com.iot.project.Equipment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/processes/{processId}/equipments")
    public List<EquipmentResponse> findAllByProcess(@PathVariable Long processId) {
        return equipmentService.findAllByProcess(processId);
    }

    @GetMapping("/equipments/{id}")
    public EquipmentResponse findById(@PathVariable Long id) {
        return equipmentService.findById(id);
    }

    @PutMapping("/equipments/{id}")
    public EquipmentResponse update(@PathVariable Long id, @Valid @RequestBody EquipmentUpdateRequest request) {
        return equipmentService.update(id, request);
    }

    @DeleteMapping("/equipments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        equipmentService.delete(id);
    }
}
