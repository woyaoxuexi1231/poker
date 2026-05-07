-- ============================================
-- Poker 项目表名迁移脚本
-- 从旧表名迁移到带 poker_ 前缀的新表名
-- ============================================

USE `poker`;

-- 1. 重命名所有表
RENAME TABLE `user` TO `poker_user`;
RENAME TABLE `persistent_logins` TO `poker_persistent_logins`;
RENAME TABLE `room` TO `poker_room`;
RENAME TABLE `room_player` TO `poker_room_player`;
RENAME TABLE `game` TO `poker_game`;
RENAME TABLE `game_player` TO `poker_game_player`;
RENAME TABLE `action_log` TO `poker_action_log`;
RENAME TABLE `transfer_log` TO `poker_transfer_log`;
RENAME TABLE `borrow_log` TO `poker_borrow_log`;

-- 2. 验证表是否都已重命名
SHOW TABLES LIKE 'poker_%';

-- 3. 验证旧表是否还存在（应该为空）
SELECT COUNT(*) as old_tables_count 
FROM information_schema.tables 
WHERE table_schema = 'poker' 
  AND table_name IN ('user', 'persistent_logins', 'room', 'room_player', 
                     'game', 'game_player', 'action_log', 'transfer_log', 'borrow_log');
