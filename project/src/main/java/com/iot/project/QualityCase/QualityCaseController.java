package com.iot.project.QualityCase;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping
    public List<QualityCaseResponse> findAll() {
        return qualityCaseService.findAll();
    }

    @GetMapping("/{id}")
    public QualityCaseResponse findById(@PathVariable Long id) {
        return qualityCaseService.findById(id);
    }

    @PatchMapping("/{id}")
    public QualityCaseResponse update(@PathVariable Long id, @Valid @RequestBody QualityCaseUpdateRequest request) {
        return qualityCaseService.update(id, request);
    }

    @PostMapping("/{id}/resolve")
    public QualityCaseResponse resolve(@PathVariable Long id, @Valid @RequestBody QualityCaseResolveRequest request) {
        return qualityCaseService.resolve(id, request);
    }

    // 이전 버전(RESOLVED 수정 직전 값) 목록. 조회만, 검색 대상 아님
    @GetMapping("/{id}/revisions")
    public List<QualityCaseRevisionResponse> findRevisions(@PathVariable Long id) {
        return qualityCaseService.findRevisions(id);
    }

    // 디버그·성능 측정용 (분석 흐름은 Analysis API)
    @GetMapping("/{id}/similar-cases")
    public List<SimilarCaseResponse> findSimilar(@PathVariable Long id) {
        return qualityCaseService.findSimilar(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        qualityCaseService.delete(id);
    }
}
