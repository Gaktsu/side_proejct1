package com.iot.project.QualityCase;

import java.util.StringJoiner;

// 임베딩할 텍스트를 만든다 (벤치마크·scripts/export_pixie_onnx.py와 같은 형식)
// 문서(toText) 필드 구성을 바꾸면 Qdrant의 기존 벡터를 모두 다시 적재해야 한다
public final class QualityCaseDocument {

    private QualityCaseDocument() {
    }

    // RESOLVED 사례 → Qdrant에 적재할 문서 텍스트
    public static String toText(QualityCase qualityCase) {
        return toQueryText(qualityCase)
                + "\n확정 원인: " + qualityCase.getRootCause()
                + "\n조치: " + qualityCase.getCorrectiveAction();
    }

    // OPEN 사례 → 검색 쿼리 텍스트. 원인·조치는 아직 없으므로 문제 발생 시점에 아는 정보만 (저장하지 않아 바꿔도 재적재 불필요)
    static String toQueryText(QualityCase qualityCase) {
        StringJoiner text = new StringJoiner("\n");
        text.add("공정: " + qualityCase.getManufacturingProcess().getName());
        if (qualityCase.getEquipment() != null) {
            text.add("설비: " + qualityCase.getEquipment().getName());
        }
        text.add("제품: " + qualityCase.getProduct().getName());
        text.add("증상: " + qualityCase.getDefectType() + " - " + qualityCase.getTitle());
        text.add("관찰: " + qualityCase.getProblemDescription());
        return text.toString();
    }
}
