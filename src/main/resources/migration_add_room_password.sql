-- 为 poker_room 表添加 password 字段
-- 执行时间：2026-05-07

USE `poker`;

ALTER TABLE `poker_room` 
ADD COLUMN `password` VARCHAR(50) NULL DEFAULT NULL COMMENT '房间密码，NULL表示无密码' 
AFTER `status`;
