package com.iot.project.QualityCase;

import com.iot.project.Equipment.Equipment;
import com.iot.project.ManufacturingProcess.ManufacturingProcess;
import com.iot.project.Product.Product;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class QualityCaseDocumentTest {

    private final Product product = new Product("TEST-P-001", "브레이크 캘리퍼", null, null);
    private final ManufacturingProcess process = new ManufacturingProcess("TEST-PR-001", "용접", null);

    private QualityCase resolvedCase(Equipment equipment) {
        QualityCase qualityCase = new QualityCase("QC-TEST", product, process, equipment, "LOT-01",
                "용접부 미세 균열", "출하 검사에서 용접부 미세 균열 발견", "크랙", 100, 3,
                LocalDateTime.of(2026, 10, 5, 9, 30));
        qualityCase.resolve("용접 전류 저하", "전류 설정 복구", "재발 없음");
        return qualityCase;
    }

    @Test
    void 설비가_있으면_공정_다음_줄에_설비() {
        Equipment equipment = new Equipment(process, "TEST-EQ-001", "용접기 1호", null);

        assertThat(QualityCaseDocument.toText(resolvedCase(equipment))).isEqualTo("""
                공정: 용접
                설비: 용접기 1호
                제품: 브레이크 캘리퍼
                증상: 크랙 - 용접부 미세 균열
                관찰: 출하 검사에서 용접부 미세 균열 발견
                확정 원인: 용접 전류 저하
                조치: 전류 설정 복구""");
    }

    @Test
    void 쿼리텍스트는_확정원인과_조치를_뺀다() {
        Equipment equipment = new Equipment(process, "TEST-EQ-001", "용접기 1호", null);

        assertThat(QualityCaseDocument.toQueryText(resolvedCase(equipment))).isEqualTo("""
                공정: 용접
                설비: 용접기 1호
                제품: 브레이크 캘리퍼
                증상: 크랙 - 용접부 미세 균열
                관찰: 출하 검사에서 용접부 미세 균열 발견""");
    }

    @Test
    void 설비가_없으면_설비_줄_생략() {
        assertThat(QualityCaseDocument.toText(resolvedCase(null))).isEqualTo("""
                공정: 용접
                제품: 브레이크 캘리퍼
                증상: 크랙 - 용접부 미세 균열
                관찰: 출하 검사에서 용접부 미세 균열 발견
                확정 원인: 용접 전류 저하
                조치: 전류 설정 복구""");
    }
}
