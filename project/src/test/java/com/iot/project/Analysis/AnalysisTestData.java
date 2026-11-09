package com.iot.project.Analysis;

import org.springframework.jdbc.core.JdbcTemplate;

// 커밋되는 테스트(처리기·동시성)용 사례. 정리는 ResolveTestData.deleteAll (분석 행까지 지움)
final class AnalysisTestData {

    private AnalysisTestData() {
    }

    static void insertMasters(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO product (product_code, name, created_at) VALUES ('TEST-P-ANALYSIS', '브레이크 캘리퍼', NOW())");
        jdbc.update("INSERT INTO manufacturing_process (process_code, name, created_at) VALUES ('TEST-PR-ANALYSIS', '용접', NOW())");
    }

    // resolved=false면 OPEN(원인·조치 NULL)
    static Long insertCase(JdbcTemplate jdbc, String caseCode, boolean resolved, int sourceVersion, boolean deleted) {
        Long productId = jdbc.queryForObject("SELECT id FROM product WHERE product_code = 'TEST-P-ANALYSIS'", Long.class);
        Long processId = jdbc.queryForObject("SELECT id FROM manufacturing_process WHERE process_code = 'TEST-PR-ANALYSIS'", Long.class);
        jdbc.update("""
                INSERT INTO quality_case (case_code, product_id, process_id, lot_no, title, problem_description,
                    defect_type, production_quantity, defect_quantity, root_cause, corrective_action, status,
                    source_version, occurred_at, created_at, updated_at, deleted_at)
                VALUES (?, ?, ?, 'LOT-01', '용접부 미세 균열', '출하 검사에서 용접부 미세 균열 발견',
                    '크랙', 100, 3, ?, ?, ?, ?, NOW(), NOW(), NOW(), ?)
                """, caseCode, productId, processId, resolved ? "용접 전류 저하" : null, resolved ? "전류 설정 복구" : null,
                resolved ? "RESOLVED" : "OPEN", sourceVersion, deleted ? java.time.LocalDateTime.now() : null);
        return jdbc.queryForObject("SELECT id FROM quality_case WHERE case_code = ?", Long.class, caseCode);
    }

    static Long insertAnalysis(JdbcTemplate jdbc, Long caseId, String status) {
        jdbc.update("INSERT INTO analysis (target_quality_case_id, analysis_status, created_at) VALUES (?, ?, NOW())", caseId, status);
        return jdbc.queryForObject("SELECT id FROM analysis WHERE target_quality_case_id = ?", Long.class, caseId);
    }
}
