CREATE TABLE `product`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `product_code` VARCHAR(50)  NOT NULL,
    `name`         VARCHAR(100) NOT NULL,
    `model_name`   VARCHAR(100) NULL,
    `description`  TEXT         NULL,
    `created_at`   DATETIME(6)  NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_product_code` UNIQUE (`product_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `manufacturing_process`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `process_code` VARCHAR(50)  NOT NULL,
    `name`         VARCHAR(100) NOT NULL,
    `description`  TEXT         NULL,
    `created_at`   DATETIME(6)  NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_manufacturing_process_code` UNIQUE (`process_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `equipment`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `process_id`     BIGINT       NOT NULL,
    `equipment_code` VARCHAR(50)  NOT NULL,
    `name`           VARCHAR(100) NOT NULL,
    `description`    TEXT         NULL,
    `created_at`     DATETIME(6)  NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_equipment_code` UNIQUE (`equipment_code`),
    CONSTRAINT `fk_equipment_manufacturing_process`
        FOREIGN KEY (`process_id`)
            REFERENCES `manufacturing_process` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `quality_case`
(
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `case_code`           VARCHAR(50)  NOT NULL,
    `product_id`          BIGINT       NOT NULL,
    `process_id`          BIGINT       NOT NULL,
    `equipment_id`        BIGINT       NULL,
    `lot_no`              VARCHAR(100) NOT NULL,
    `title`               VARCHAR(200) NOT NULL,
    `problem_description` TEXT         NOT NULL,
    `defect_type`         VARCHAR(50)  NOT NULL,
    `production_quantity` INT          NOT NULL,
    `defect_quantity`     INT          NOT NULL,
    `root_cause`          TEXT         NULL,
    `corrective_action`   TEXT         NULL,
    `result_summary`      TEXT         NULL,
    `status`              VARCHAR(30)  NOT NULL,
    `occurred_at`         DATETIME(6)  NOT NULL,
    `created_at`          DATETIME(6)  NOT NULL,
    `updated_at`          DATETIME(6)  NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_quality_case_code` UNIQUE (`case_code`),
    CONSTRAINT `fk_quality_case_product`
        FOREIGN KEY (`product_id`)
            REFERENCES `product` (`id`),
    CONSTRAINT `fk_quality_case_manufacturing_process`
        FOREIGN KEY (`process_id`)
            REFERENCES `manufacturing_process` (`id`),
    CONSTRAINT `fk_quality_case_equipment`
        FOREIGN KEY (`equipment_id`)
            REFERENCES `equipment` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `inspection_result`
(
    `id`                BIGINT         NOT NULL AUTO_INCREMENT,
    `quality_case_id`   BIGINT         NOT NULL,
    `inspection_item`   VARCHAR(100)   NOT NULL,
    `inspection_method` VARCHAR(100)   NULL,
    `numeric_value`     DECIMAL(18, 6) NULL,
    `text_value`        VARCHAR(255)   NULL,
    `unit`              VARCHAR(30)    NULL,
    `spec_min`          DECIMAL(18, 6) NULL,
    `spec_max`          DECIMAL(18, 6) NULL,
    `spec_text`         VARCHAR(255)   NULL,
    `result_status`     VARCHAR(20)    NOT NULL,
    `measured_at`       DATETIME(6)    NULL,
    `created_at`        DATETIME(6)    NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_inspection_result_quality_case`
        FOREIGN KEY (`quality_case_id`)
            REFERENCES `quality_case` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `analysis`
(
    `id`                       BIGINT      NOT NULL AUTO_INCREMENT,
    `target_quality_case_id`   BIGINT      NOT NULL,
    `query_text`               TEXT        NOT NULL,
    `analysis_status`          VARCHAR(30) NOT NULL,
    `ai_root_cause_candidates` JSON        NULL,
    `ai_check_items`           JSON        NULL,
    `ai_summary`               TEXT        NULL,
    `created_at`               DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_analysis_target_quality_case`
        FOREIGN KEY (`target_quality_case_id`)
            REFERENCES `quality_case` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `analysis_candidate`
(
    `id`                        BIGINT        NOT NULL AUTO_INCREMENT,
    `analysis_id`               BIGINT        NOT NULL,
    `candidate_quality_case_id` BIGINT        NOT NULL,
    `qdrant_rank`               INT           NOT NULL,
    `vector_score`              DECIMAL(8, 6) NOT NULL,
    `jev_score`                 DECIMAL(8, 6) NULL,
    `is_null`                   BOOLEAN       NOT NULL,
    `decision_reason`           TEXT          NULL,
    `created_at`                DATETIME(6)   NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_analysis_candidate`
        UNIQUE (`analysis_id`, `candidate_quality_case_id`),
    CONSTRAINT `fk_analysis_candidate_analysis`
        FOREIGN KEY (`analysis_id`)
            REFERENCES `analysis` (`id`),
    CONSTRAINT `fk_analysis_candidate_quality_case`
        FOREIGN KEY (`candidate_quality_case_id`)
            REFERENCES `quality_case` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;