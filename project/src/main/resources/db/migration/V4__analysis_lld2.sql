-- LLD 2: 분석 요청·후보 저장 (JEV/LLM 전)
-- 검색 텍스트는 실행 시점 사례로 만들고 저장하지 않는다 (로그만). 사례당 분석은 최대 1개
ALTER TABLE `analysis`
    DROP COLUMN `query_text`,
    ADD COLUMN `error_message` TEXT NULL AFTER `analysis_status`,
    ADD CONSTRAINT `uk_analysis_target_quality_case` UNIQUE (`target_quality_case_id`);

-- is_null: NULL = JEV 미평가. invalid_reason: 근거로 못 쓰는 후보의 사유 (NULL = 사용 가능)
ALTER TABLE `analysis_candidate`
    MODIFY COLUMN `is_null` BOOLEAN NULL,
    ADD COLUMN `invalid_reason` VARCHAR(30) NULL AFTER `vector_score`;
