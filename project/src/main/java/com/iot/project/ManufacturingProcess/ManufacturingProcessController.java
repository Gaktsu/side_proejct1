package com.iot.project.ManufacturingProcess;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        manufacturingProcessService.delete(id);
    }
}
