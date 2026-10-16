package com.iot.project.QualityCase;

import org.springframework.jdbc.core.JdbcTemplate;

// 커밋되는 resolve 테스트(롤백·동시성)용 데이터. 테스트 트랜잭션이 없으므로 직접 넣고 지운다
final class ResolveTestData {

    private ResolveTestData() {
    }

    static Long insertOpenCase(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO product (product_code, name, created_at) VALUES ('TEST-P-RESOLVE', '브레이크 캘리퍼', NOW())");
        jdbc.update("INSERT INTO manufacturing_process (process_code, name, created_at) VALUES ('TEST-PR-RESOLVE', '용접', NOW())");
        Long productId = jdbc.queryForObject("SELECT id FROM product WHERE product_code = 'TEST-P-RESOLVE'", Long.class);
        Long processId = jdbc.queryForObject("SELECT id FROM manufacturing_process WHERE process_code = 'TEST-PR-RESOLVE'", Long.class);
        jdbc.update("""
                INSERT INTO quality_case (case_code, product_id, process_id, lot_no, title, problem_description,
                    defect_type, production_quantity, defect_quantity, status, occurred_at, created_at, updated_at)
                VALUES ('TEST-QC-RESOLVE', ?, ?, 'LOT-01', '용접부 미세 균열', '출하 검사에서 용접부 미세 균열 발견',
                    '크랙', 100, 3, 'OPEN', NOW(), NOW(), NOW())
                """, productId, processId);
        return jdbc.queryForObject("SELECT id FROM quality_case WHERE case_code = 'TEST-QC-RESOLVE'", Long.class);
    }

    // 자식 테이블부터 지운다
    static void deleteAll(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM quality_case WHERE case_code LIKE 'TEST-%'");
        jdbc.update("DELETE FROM manufacturing_process WHERE process_code LIKE 'TEST-%'");
        jdbc.update("DELETE FROM product WHERE product_code LIKE 'TEST-%'");
    }
}
