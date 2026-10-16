package com.iot.project.QualityCase;

import java.util.StringJoiner;

// RESOLVED 사례를 임베딩할 문서 텍스트로 만든다 (벤치마크·scripts/export_pixie_onnx.py와 같은 형식)
// 필드 구성을 바꾸면 Qdrant의 기존 벡터를 모두 다시 적재해야 한다
final class QualityCaseDocument {

    private QualityCaseDocument() {
    }

    static String toText(QualityCase qualityCase) {
        StringJoiner text = new StringJoiner("\n");
        text.add("공정: " + qualityCase.getManufacturingProcess().getName());
        if (qualityCase.getEquipment() != null) {
            text.add("설비: " + qualityCase.getEquipment().getName());
        }
        text.add("제품: " + qualityCase.getProduct().getName());
        text.add("증상: " + qualityCase.getDefectType() + " - " + qualityCase.getTitle());
        text.add("관찰: " + qualityCase.getProblemDescription());
        text.add("확정 원인: " + qualityCase.getRootCause());
        text.add("조치: " + qualityCase.getCorrectiveAction());
        return text.toString();
    }
}
