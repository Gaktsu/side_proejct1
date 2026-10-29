-- LLD 1: 해결 상태와 검색 반영(인덱싱) 상태를 분리하고, RESOLVED 수정 시 직전 내용을 남긴다

-- 현재 내용의 번호. 등록 0, 해결 시 +1, RESOLVED 수정마다 +1
ALTER TABLE `quality_case`
    ADD COLUMN `source_version` INT NOT NULL DEFAULT 0;

CREATE TABLE `indexing_job`
(
    `id`               BIGINT      NOT NULL AUTO_INCREMENT,
    `quality_case_id`  BIGINT      NOT NULL,
    `source_version`   INT         NOT NULL,
    `embedding_status` VARCHAR(20) NOT NULL,
    `qdrant_status`    VARCHAR(20) NOT NULL,
    `last_error`       TEXT        NULL,
    `created_at`       DATETIME(6) NOT NULL,
    `updated_at`       DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_indexing_job_case_version` UNIQUE (`quality_case_id`, `source_version`),
    CONSTRAINT `fk_indexing_job_quality_case`
        FOREIGN KEY (`quality_case_id`)
            REFERENCES `quality_case` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `quality_case_revision`
(
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `quality_case_id`     BIGINT       NOT NULL,
    `version`             INT          NOT NULL,
    `product_id`          BIGINT       NOT NULL,
    `process_id`          BIGINT       NOT NULL,
    `equipment_id`        BIGINT       NULL,
    `lot_no`              VARCHAR(100) NOT NULL,
    `title`               VARCHAR(200) NOT NULL,
    `problem_description` TEXT         NOT NULL,
    `defect_type`         VARCHAR(50)  NOT NULL,
    `production_quantity` INT          NOT NULL,
    `defect_quantity`     INT          NOT NULL,
    `occurred_at`         DATETIME(6)  NOT NULL,
    `root_cause`          TEXT         NULL,
    `corrective_action`   TEXT         NULL,
    `result_summary`      TEXT         NULL,
    `created_at`          DATETIME(6)  NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_quality_case_revision_version` UNIQUE (`quality_case_id`, `version`),
    CONSTRAINT `fk_quality_case_revision_quality_case`
        FOREIGN KEY (`quality_case_id`)
            REFERENCES `quality_case` (`id`),
    CONSTRAINT `fk_quality_case_revision_product`
        FOREIGN KEY (`product_id`)
            REFERENCES `product` (`id`),
    CONSTRAINT `fk_quality_case_revision_manufacturing_process`
        FOREIGN KEY (`process_id`)
            REFERENCES `manufacturing_process` (`id`),
    CONSTRAINT `fk_quality_case_revision_equipment`
        FOREIGN KEY (`equipment_id`)
            REFERENCES `equipment` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 이미 RESOLVED인 사례는 payload 없는 옛 벡터라 searchable 필터에 걸려 검색에서 빠진다 → 버전 1로 두고 다시 적재
UPDATE `quality_case`
SET `source_version` = 1
WHERE `status` = 'RESOLVED';

INSERT INTO `indexing_job` (`quality_case_id`, `source_version`, `embedding_status`, `qdrant_status`, `created_at`, `updated_at`)
SELECT `id`, 1, 'PENDING', 'WAITING', NOW(6), NOW(6)
FROM `quality_case`
WHERE `status` = 'RESOLVED';
