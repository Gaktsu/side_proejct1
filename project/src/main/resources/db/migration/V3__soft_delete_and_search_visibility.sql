-- LLD 4: 논리 삭제와 Qdrant 검색 노출(searchable) 반영 작업

-- NULL이면 삭제 안 됨. 업무 상태(status)와 섞지 않는다
ALTER TABLE `quality_case`
    ADD COLUMN `deleted_at` DATETIME(6) NULL;

-- 사례의 searchable을 MySQL 현재 상태에 맞추라는 할 일. 목표 값·버전은 저장하지 않고 실행 시 MySQL을 읽어 정한다
CREATE TABLE `search_visibility_job`
(
    `id`              BIGINT      NOT NULL AUTO_INCREMENT,
    `quality_case_id` BIGINT      NOT NULL,
    `status`          VARCHAR(20) NOT NULL,
    `last_error`      TEXT        NULL,
    `created_at`      DATETIME(6) NOT NULL,
    `updated_at`      DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_search_visibility_job_quality_case`
        FOREIGN KEY (`quality_case_id`)
            REFERENCES `quality_case` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
