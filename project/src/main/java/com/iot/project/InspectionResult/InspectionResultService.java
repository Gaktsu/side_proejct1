package com.iot.project.InspectionResult;

import com.iot.project.QualityCase.QualityCase;
import com.iot.project.QualityCase.QualityCaseNotFoundException;
import com.iot.project.QualityCase.QualityCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

// 검사 결과는 임베딩·LLM에 쓰지 않으므로 사례 상태와 무관하게 바꿀 수 있고 버전·인덱싱을 건드리지 않는다
@Service
@RequiredArgsConstructor
public class InspectionResultService {

    private final InspectionResultRepository inspectionResultRepository;
    private final QualityCaseRepository qualityCaseRepository;

    @Transactional
    public InspectionResultResponse create(Long qualityCaseId, InspectionResultRequest request) {
        validateValueAndSpec(request);
        QualityCase qualityCase = qualityCaseRepository.findByIdAndDeletedAtIsNull(qualityCaseId)
                .orElseThrow(() -> new QualityCaseNotFoundException(qualityCaseId));
        InspectionResult result = new InspectionResult(qualityCase, request.inspectionItem(),
                request.inspectionMethod(), request.numericValue(), request.textValue(), request.unit(),
                request.specMin(), request.specMax(), request.specText(), request.resultStatus(),
                request.measuredAt());
        return InspectionResultResponse.from(inspectionResultRepository.save(result));
    }

    @Transactional
    public InspectionResultResponse update(Long id, InspectionResultRequest request) {
        validateValueAndSpec(request);
        InspectionResult result = getInspectionResult(id);
        result.update(request.inspectionItem(), request.inspectionMethod(), request.numericValue(),
                request.textValue(), request.unit(), request.specMin(), request.specMax(), request.specText(),
                request.resultStatus(), request.measuredAt());
        return InspectionResultResponse.from(result);
    }

    @Transactional
    public void delete(Long id) {
        inspectionResultRepository.delete(getInspectionResult(id));
    }

    // 측정값이 규격 밖인지는 보지 않는다: 불량을 기록해야 하고 PASS/FAIL은 담당자 입력값이다
    private void validateValueAndSpec(InspectionResultRequest request) {
        if (request.numericValue() == null && !StringUtils.hasText(request.textValue())) {
            throw new InvalidInspectionResultException("숫자 측정값과 문자 검사값 중 하나는 입력해야 합니다.");
        }
        if (request.specMin() != null && request.specMax() != null
                && request.specMin().compareTo(request.specMax()) > 0) {
            throw new InvalidInspectionResultException(
                    "규격 최소값은 최대값보다 클 수 없습니다. specMin=" + request.specMin() + ", specMax=" + request.specMax());
        }
    }

    private InspectionResult getInspectionResult(Long id) {
        return inspectionResultRepository.findByIdAndQualityCase_DeletedAtIsNull(id)
                .orElseThrow(() -> new InspectionResultNotFoundException(id));
    }
}
