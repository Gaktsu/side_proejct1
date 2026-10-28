package com.iot.project.ManufacturingProcess;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/processes")
@RequiredArgsConstructor
public class ManufacturingProcessController {

    private final ManufacturingProcessService manufacturingProcessService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManufacturingProcessResponse create(@Valid @RequestBody ManufacturingProcessCreateRequest request) {
        return manufacturingProcessService.create(request);
    }

    @GetMapping
    public List<ManufacturingProcessResponse> findAll() {
        return manufacturingProcessService.findAll();
    }

    @GetMapping("/{id}")
    public ManufacturingProcessResponse findById(@PathVariable Long id) {
        return manufacturingProcessService.findById(id);
    }

    @PutMapping("/{id}")
    public ManufacturingProcessResponse update(@PathVariable Long id,
                                               @Valid @RequestBody ManufacturingProcessUpdateRequest request) {
        return manufacturingProcessService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        manufacturingProcessService.delete(id);
    }
}
