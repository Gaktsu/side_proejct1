package com.iot.project.QualityCase;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quality-cases")
@RequiredArgsConstructor
public class QualityCaseController {

    private final QualityCaseService qualityCaseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QualityCaseResponse create(@Valid @RequestBody QualityCaseCreateRequest request) {
        return qualityCaseService.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        qualityCaseService.delete(id);
    }
}
