CREATE DATABASE IF NOT EXISTS `poker` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE `poker`;

DROP TABLE IF EXISTS `action_log`;
DROP TABLE IF EXISTS `game_player`;
DROP TABLE IF EXISTS `game`;
DROP TABLE IF EXISTS `transfer_log`;
DROP TABLE IF EXISTS `borrow_log`;
DROP TABLE IF EXISTS `room_player`;
DROP TABLE IF EXISTS `room`;
DROP TABLE IF EXISTS `persistent_logins`;
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL,
  `password` VARCHAR(255) NOT NULL,
  `nickname` VARCHAR(50) NOT NULL,
  `created_time` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `persistent_logins` (
  `username` VARCHAR(64) NOT NULL,
  `series` VARCHAR(64) NOT NULL,
  `token` VARCHAR(64) NOT NULL,
  `last_used` TIMESTAMP NOT NULL,
  PRIMARY KEY (`series`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `room` (
  `room_id` VARCHAR(6) NOT NULL,
  `created_by` BIGINT NOT NULL,
  `created_time` DATETIME NOT NULL,
  PRIMARY KEY (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `room_player` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `room_id` VARCHAR(6) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `balance` INT NOT NULL DEFAULT 200,
  `borrowed_total` INT NOT NULL DEFAULT 0,
  `is_active` TINYINT(1) NOT NULL DEFAULT 1,
  `joined_time` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_user` (`room_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `game` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `room_id` VARCHAR(6) NOT NULL,
  `round_number` INT NOT NULL DEFAULT 1,
  `pot` INT NOT NULL DEFAULT 0,
  `is_finished` TINYINT(1) NOT NULL DEFAULT 0,
  `created_time` DATETIME NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `game_player` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `game_id` BIGINT NOT NULL,
  `room_player_id` BIGINT NOT NULL,
  `pending_bet` INT NOT NULL DEFAULT 0,
  `current_round_bet` INT NOT NULL DEFAULT 0,
  `total_bet` INT NOT NULL DEFAULT 0,
  `is_folded` TINYINT(1) NOT NULL DEFAULT 0,
  `is_bet_confirmed` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_game_player` (`game_id`, `room_player_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `action_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `game_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `action_type` VARCHAR(20) NOT NULL,
  `amount` INT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `transfer_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `room_id` VARCHAR(6) NOT NULL,
  `from_user_id` BIGINT NOT NULL,
  `to_user_id` BIGINT NOT NULL,
  `amount` INT NOT NULL,
  `create_time` DATETIME NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `borrow_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `room_id` VARCHAR(6) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `amount` INT NOT NULL,
  `create_time` DATETIME NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
