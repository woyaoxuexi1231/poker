CREATE DATABASE IF NOT EXISTS `poker` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE `poker`;

CREATE TABLE IF NOT EXISTS `poker_user`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `username`     VARCHAR(50)  NOT NULL,
    `password`     VARCHAR(255) NOT NULL,
    `nickname`     VARCHAR(50)  NOT NULL,
    `avatar`       VARCHAR(10)  NOT NULL DEFAULT '🦁',
    `created_time` DATETIME     NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `poker_persistent_logins`
(
    `username`  VARCHAR(64) NOT NULL,
    `series`    VARCHAR(64) NOT NULL,
    `token`     VARCHAR(64) NOT NULL,
    `last_used` TIMESTAMP   NOT NULL,
    PRIMARY KEY (`series`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `poker_room`
(
    `room_id`      VARCHAR(6) NOT NULL,
    `created_by`   BIGINT     NOT NULL,
    `created_time` DATETIME   NOT NULL,
    `status`       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE-活跃 DISSOLVED-已解散',
    `password`     VARCHAR(50) NULL DEFAULT NULL COMMENT '房间密码，NULL表示无密码',
    PRIMARY KEY (`room_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `poker_room_player`
(
    `id`             BIGINT     NOT NULL AUTO_INCREMENT,
    `room_id`        VARCHAR(6) NOT NULL,
    `user_id`        BIGINT     NOT NULL,
    `balance`        INT        NOT NULL DEFAULT 200,
    `borrowed_total` INT        NOT NULL DEFAULT 0,
    `is_active`      TINYINT(1) NOT NULL DEFAULT 1,
    `joined_time`    DATETIME   NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_user` (`room_id`, `user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `poker_game`
(
    `id`                  BIGINT     NOT NULL AUTO_INCREMENT,
    `room_id`             VARCHAR(6) NOT NULL,
    `phase`               VARCHAR(20) NOT NULL DEFAULT 'PRE_FLOP',
    `current_highest_bet` INT        NOT NULL DEFAULT 0,
    `pot`                 INT        NOT NULL DEFAULT 0,
    `is_finished`         TINYINT(1) NOT NULL DEFAULT 0,
    `pre_flop_cap`        INT        NOT NULL DEFAULT 0,
    `flop_cap`            INT        NOT NULL DEFAULT 0,
    `turn_cap`            INT        NOT NULL DEFAULT 0,
    `river_cap`           INT        NOT NULL DEFAULT 0,
    `created_time`        DATETIME   NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `poker_game_player`
(
    `id`                BIGINT     NOT NULL AUTO_INCREMENT,
    `game_id`           BIGINT     NOT NULL,
    `room_player_id`    BIGINT     NOT NULL,
    `pending_bet`       INT        NOT NULL DEFAULT 0,
    `current_round_bet` INT        NOT NULL DEFAULT 0,
    `total_bet`         INT        NOT NULL DEFAULT 0,
    `is_folded`         TINYINT(1) NOT NULL DEFAULT 0,
    `is_bet_confirmed`  TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_game_player` (`game_id`, `room_player_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `poker_action_log`
(
    `id`           BIGINT      NOT NULL AUTO_INCREMENT,
    `game_id`      BIGINT      NOT NULL,
    `user_id`      BIGINT      NOT NULL,
    `action_type`  VARCHAR(20) NOT NULL,
    `amount`       INT         NOT NULL DEFAULT 0,
    `round_number` INT         NOT NULL DEFAULT 0,
    `create_time`  DATETIME    NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `poker_transfer_log`
(
    `id`           BIGINT     NOT NULL AUTO_INCREMENT,
    `room_id`      VARCHAR(6) NOT NULL,
    `from_user_id` BIGINT     NOT NULL,
    `to_user_id`   BIGINT     NOT NULL,
    `amount`       INT        NOT NULL,
    `create_time`  DATETIME   NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `poker_borrow_log`
(
    `id`          BIGINT     NOT NULL AUTO_INCREMENT,
    `room_id`     VARCHAR(6) NOT NULL,
    `user_id`     BIGINT     NOT NULL,
    `amount`      INT        NOT NULL,
    `create_time` DATETIME   NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
