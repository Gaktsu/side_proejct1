package com.iot.project.Analysis;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    // 접수(202)만 하고 검색은 백그라운드에서. 결과는 GET /api/analyses/{analysisId}
    @PostMapping("/quality-cases/{id}/analyses")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisAcceptedResponse request(@PathVariable Long id) {
        return analysisService.request(id);
    }

    @GetMapping("/analyses/{analysisId}")
    public AnalysisResponse findById(@PathVariable Long analysisId) {
        return analysisService.findById(analysisId);
    }
}
