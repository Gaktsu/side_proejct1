package com.iot.project.QualityCase;

import org.springframework.jdbc.core.JdbcTemplate;

// 커밋되는 테스트(동시성·인덱싱 처리기)용 데이터. 테스트 트랜잭션이 없으므로 직접 넣고 지운다
public final class ResolveTestData {

    private ResolveTestData() {
    }

    public static Long insertOpenCase(JdbcTemplate jdbc) {
        return insertCase(jdbc, "OPEN", null, 0);
    }

    // 문서 텍스트에 들어가는 원인·조치까지 채운 RESOLVED 사례
    public static Long insertResolvedCase(JdbcTemplate jdbc, int sourceVersion) {
        return insertCase(jdbc, "RESOLVED", "용접 전류 저하", sourceVersion);
    }

    private static Long insertCase(JdbcTemplate jdbc, String status, String rootCause, int sourceVersion) {
        jdbc.update("INSERT INTO product (product_code, name, created_at) VALUES ('TEST-P-RESOLVE', '브레이크 캘리퍼', NOW())");
        jdbc.update("INSERT INTO manufacturing_process (process_code, name, created_at) VALUES ('TEST-PR-RESOLVE', '용접', NOW())");
        Long productId = jdbc.queryForObject("SELECT id FROM product WHERE product_code = 'TEST-P-RESOLVE'", Long.class);
        Long processId = jdbc.queryForObject("SELECT id FROM manufacturing_process WHERE process_code = 'TEST-PR-RESOLVE'", Long.class);
        jdbc.update("""
                INSERT INTO quality_case (case_code, product_id, process_id, lot_no, title, problem_description,
                    defect_type, production_quantity, defect_quantity, root_cause, corrective_action, status,
                    source_version, occurred_at, created_at, updated_at)
                VALUES ('TEST-QC-RESOLVE', ?, ?, 'LOT-01', '용접부 미세 균열', '출하 검사에서 용접부 미세 균열 발견',
                    '크랙', 100, 3, ?, ?, ?, ?, NOW(), NOW(), NOW())
                """, productId, processId, rootCause, rootCause == null ? null : "전류 설정 복구", status, sourceVersion);
        return jdbc.queryForObject("SELECT id FROM quality_case WHERE case_code = 'TEST-QC-RESOLVE'", Long.class);
    }

    public static Long insertJob(JdbcTemplate jdbc, Long caseId, int sourceVersion, String embeddingStatus, String qdrantStatus) {
        jdbc.update("""
                INSERT INTO indexing_job (quality_case_id, source_version, embedding_status, qdrant_status, created_at, updated_at)
                VALUES (?, ?, ?, ?, NOW(), NOW())
                """, caseId, sourceVersion, embeddingStatus, qdrantStatus);
        return jdbc.queryForObject("SELECT id FROM indexing_job WHERE quality_case_id = ? AND source_version = ?",
                Long.class, caseId, sourceVersion);
    }

    // 자식 테이블부터 지운다
    public static void deleteAll(JdbcTemplate jdbc) {
        jdbc.update("DELETE j FROM indexing_job j JOIN quality_case q ON j.quality_case_id = q.id WHERE q.case_code LIKE 'TEST-%'");
        jdbc.update("DELETE r FROM quality_case_revision r JOIN quality_case q ON r.quality_case_id = q.id WHERE q.case_code LIKE 'TEST-%'");
        jdbc.update("DELETE FROM quality_case WHERE case_code LIKE 'TEST-%'");
        jdbc.update("DELETE FROM manufacturing_process WHERE process_code LIKE 'TEST-%'");
        jdbc.update("DELETE FROM product WHERE product_code LIKE 'TEST-%'");
    }
}
