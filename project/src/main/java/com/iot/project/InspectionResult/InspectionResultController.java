package com.iot.project.InspectionResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InspectionResultController {

    private final InspectionResultService inspectionResultService;

    @PostMapping("/quality-cases/{qualityCaseId}/inspection-results")
    @ResponseStatus(HttpStatus.CREATED)
    public InspectionResultResponse create(@PathVariable Long qualityCaseId,
                                           @Valid @RequestBody InspectionResultRequest request) {
        return inspectionResultService.create(qualityCaseId, request);
    }

    @PutMapping("/inspection-results/{id}")
    public InspectionResultResponse update(@PathVariable Long id, @Valid @RequestBody InspectionResultRequest request) {
        return inspectionResultService.update(id, request);
    }

    @DeleteMapping("/inspection-results/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        inspectionResultService.delete(id);
    }
}
