package com.iot.project.QualityCase;

import jakarta.validation.constraints.NotBlank;

// 원인/조치는 사례 문서 텍스트에 들어가므로 필수, 결과는 조치 직후 모를 수 있어 선택
public record QualityCaseResolveRequest(
        @NotBlank String rootCause,
        @NotBlank String correctiveAction,
        String resultSummary
) {
}
