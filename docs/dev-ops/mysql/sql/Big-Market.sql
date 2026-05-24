/*
 Navicat Premium Dump SQL

 Source Server         : agent
 Source Server Type    : MySQL
 Source Server Version : 80032 (8.0.32)
 Source Host           : 159.75.79.79:13306
 Source Schema         : Big-Market

 Target Server Type    : MySQL
 Target Server Version : 80032 (8.0.32)
 File Encoding         : 65001

 Date: 24/05/2026 16:58:56
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for activity
-- ----------------------------
DROP TABLE IF EXISTS `activity`;
CREATE TABLE `activity`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `activity_id` bigint NOT NULL COMMENT '活动ID(业务主键)',
  `activity_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '活动名称',
  `activity_desc` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '活动描述',
  `strategy_id` bigint NOT NULL COMMENT '关联的抽奖策略ID',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-待审核 1-上线 2-下线 3-已结束',
  `begin_time` datetime NOT NULL COMMENT '活动开始时间',
  `end_time` datetime NOT NULL COMMENT '活动结束时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_activity_id`(`activity_id` ASC) USING BTREE,
  INDEX `idx_strategy_id`(`strategy_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '活动主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of activity
-- ----------------------------
INSERT INTO `activity` VALUES (1, 20001, '618年中大促抽奖', '618大促活动, 多种方式参与抽奖, 奖品丰厚', 100001, 1, '2025-06-01 00:00:00', '2027-06-30 23:59:59', '2026-05-23 16:08:12', '2026-05-24 16:25:48');

-- ----------------------------
-- Table structure for activity_account
-- ----------------------------
DROP TABLE IF EXISTS `activity_account`;
CREATE TABLE `activity_account`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户ID',
  `activity_id` bigint NOT NULL COMMENT '活动ID',
  `total_count` int NOT NULL DEFAULT 0 COMMENT '总可用次数',
  `total_count_surplus` int NOT NULL DEFAULT 0 COMMENT '总剩余次数',
  `month_count` int NOT NULL DEFAULT 0 COMMENT '月可用次数',
  `month_count_surplus` int NOT NULL DEFAULT 0 COMMENT '月剩余次数',
  `day_count` int NOT NULL DEFAULT 0 COMMENT '日可用次数',
  `day_count_surplus` int NOT NULL DEFAULT 0 COMMENT '日剩余次数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_activity`(`user_id` ASC, `activity_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户活动账户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of activity_account
-- ----------------------------
INSERT INTO `activity_account` VALUES (1, 'user_001', 20001, 30, 29, 10, 9, 3, 2, '2026-05-23 16:08:12', '2026-05-24 16:58:26');
INSERT INTO `activity_account` VALUES (2, 'user_002', 20001, 30, 22, 10, 7, 3, 1, '2026-05-23 16:08:12', '2026-05-23 16:08:12');
INSERT INTO `activity_account` VALUES (3, 'user_003', 20001, 30, 5, 10, 2, 3, 0, '2026-05-23 16:08:12', '2026-05-23 16:08:12');

-- ----------------------------
-- Table structure for activity_count
-- ----------------------------
DROP TABLE IF EXISTS `activity_count`;
CREATE TABLE `activity_count`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `activity_count_id` bigint NOT NULL COMMENT '额度配置ID(业务主键)',
  `total_count` int NOT NULL DEFAULT 0 COMMENT '总参与次数上限, -1表示不限',
  `month_count` int NOT NULL DEFAULT 0 COMMENT '月参与次数上限, -1表示不限',
  `day_count` int NOT NULL DEFAULT 0 COMMENT '日参与次数上限, -1表示不限',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_activity_count_id`(`activity_count_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '活动额度配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of activity_count
-- ----------------------------
INSERT INTO `activity_count` VALUES (1, 10001, 30, 10, 3, '2026-05-23 16:08:12', '2026-05-23 16:08:12');
INSERT INTO `activity_count` VALUES (2, 10002, -1, 50, 10, '2026-05-23 16:08:12', '2026-05-23 16:08:12');
INSERT INTO `activity_count` VALUES (3, 10003, 10, 5, 2, '2026-05-23 16:08:12', '2026-05-23 16:08:12');

-- ----------------------------
-- Table structure for activity_order
-- ----------------------------
DROP TABLE IF EXISTS `activity_order`;
CREATE TABLE `activity_order`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `order_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订单ID(业务主键, 雪花/UUID)',
  `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户ID',
  `activity_id` bigint NOT NULL COMMENT '活动ID',
  `sku_id` bigint NOT NULL COMMENT '消费的SKU ID',
  `strategy_id` bigint NOT NULL COMMENT '关联的策略ID(冗余, 方便抽奖时直接使用)',
  `order_status` tinyint NOT NULL DEFAULT 0 COMMENT '0-create待使用 1-used已使用 2-expired已过期',
  `points_cost` int NOT NULL DEFAULT 0 COMMENT '本次消耗的积分',
  `out_business_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '外部业务单号(幂等键, 防重复提交)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_id`(`order_id` ASC) USING BTREE,
  INDEX `idx_user_activity`(`user_id` ASC, `activity_id` ASC) USING BTREE,
  INDEX `idx_out_business_no`(`out_business_no` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户参与订单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of activity_order
-- ----------------------------
INSERT INTO `activity_order` VALUES (1, 'ORD20250605001', 'user_002', 20001, 30001, 100001, 1, 0, 'BIZ20250605001', '2025-06-05 10:29:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (2, 'ORD20250605002', 'user_002', 20001, 30001, 100001, 1, 0, 'BIZ20250605002', '2025-06-05 10:30:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (3, 'ORD20250605003', 'user_002', 20001, 30001, 100001, 1, 0, 'BIZ20250605003', '2025-06-05 10:31:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (4, 'ORD20250605004', 'user_002', 20001, 30002, 100001, 1, 50, 'BIZ20250605004', '2025-06-05 10:32:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (5, 'ORD20250605005', 'user_002', 20001, 30002, 100001, 0, 50, 'BIZ20250605005', '2025-06-05 10:35:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (6, 'ORD20250603001', 'user_003', 20001, 30001, 100001, 1, 0, 'BIZ20250603001', '2025-06-03 14:00:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (7, 'ORD20250603002', 'user_003', 20001, 30001, 100001, 1, 0, 'BIZ20250603002', '2025-06-03 14:01:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (8, 'ORD20250603003', 'user_003', 20001, 30001, 100001, 1, 0, 'BIZ20250603003', '2025-06-03 14:02:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (9, 'ORD20250603004', 'user_003', 20001, 30002, 100001, 1, 50, 'BIZ20250603004', '2025-06-03 14:03:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (10, 'ORD20250603005', 'user_003', 20001, 30002, 100001, 1, 50, 'BIZ20250603005', '2025-06-03 14:04:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (11, 'ORD20250604001', 'user_003', 20001, 30003, 100001, 1, 0, 'BIZ20250604001', '2025-06-04 09:00:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (12, 'ORD20250604002', 'user_003', 20001, 30002, 100001, 1, 50, 'BIZ20250604002', '2025-06-04 09:01:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (13, 'ORD20250604003', 'user_003', 20001, 30002, 100001, 1, 50, 'BIZ20250604003', '2025-06-04 09:02:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (14, 'ORD20250604004', 'user_003', 20001, 30001, 100001, 1, 0, 'BIZ20250604004', '2025-06-04 09:03:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (15, 'ORD20250604005', 'user_003', 20001, 30002, 100001, 1, 50, 'BIZ20250604005', '2025-06-04 09:04:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (16, 'ORD20250604006', 'user_003', 20001, 30001, 100001, 1, 0, 'BIZ20250604006', '2025-06-04 09:05:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (17, 'ORD20250604007', 'user_003', 20001, 30002, 100001, 1, 50, 'BIZ20250604007', '2025-06-04 09:06:00', '2026-05-23 16:08:12');
INSERT INTO `activity_order` VALUES (18, 'ORD20250601001', 'user_003', 20001, 30003, 100001, 2, 0, 'BIZ20250601001', '2025-06-01 08:00:00', '2026-05-23 16:08:12');

-- ----------------------------
-- Table structure for activity_sku
-- ----------------------------
DROP TABLE IF EXISTS `activity_sku`;
CREATE TABLE `activity_sku`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID(业务主键)',
  `activity_id` bigint NOT NULL COMMENT '所属活动ID',
  `sku_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'SKU名称, 如免费抽奖、50积分抽一次',
  `sku_type` tinyint NOT NULL DEFAULT 1 COMMENT '1-免费 2-积分兑换 3-分享获得 4-充值赠送',
  `points_cost` int NOT NULL DEFAULT 0 COMMENT '消耗积分数(sku_type=2时有效)',
  `activity_count_id` bigint NOT NULL COMMENT '关联的额度配置ID',
  `stock_count` int NOT NULL DEFAULT 0 COMMENT 'SKU总库存',
  `stock_surplus` int NOT NULL DEFAULT 0 COMMENT 'SKU剩余库存',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '0-下架 1-上架',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sku_id`(`sku_id` ASC) USING BTREE,
  INDEX `idx_activity_id`(`activity_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '活动SKU表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of activity_sku
-- ----------------------------
INSERT INTO `activity_sku` VALUES (1, 30001, 20001, '每日免费抽奖', 1, 0, 10001, 100000, 99950, 1, '2026-05-23 16:08:12', '2026-05-23 16:08:12');
INSERT INTO `activity_sku` VALUES (2, 30002, 20001, '50积分抽一次', 2, 50, 10002, 50000, 49800, 1, '2026-05-23 16:08:12', '2026-05-23 16:08:12');
INSERT INTO `activity_sku` VALUES (3, 30003, 20001, '分享好友得抽奖机会', 3, 0, 10003, 10000, 9990, 1, '2026-05-23 16:08:12', '2026-05-23 16:08:12');

-- ----------------------------
-- Table structure for award
-- ----------------------------
DROP TABLE IF EXISTS `award`;
CREATE TABLE `award`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `award_id` int NOT NULL COMMENT '奖品ID(业务主键)',
  `award_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '奖品对接标识, 如user_points, coupon_center, random_points, physical_goods',
  `award_config` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '对接配置JSON, 如{\"min\":1,\"max\":100}',
  `award_desc` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '奖品描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_award_id`(`award_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '奖品配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of award
-- ----------------------------
INSERT INTO `award` VALUES (1, 101, 'physical_goods', '{\"sku\":\"IPHONE15_256G\",\"delivery\":\"express\"}', '一等奖-iPhone15', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `award` VALUES (2, 102, 'coupon_center', '{\"coupon_id\":\"COUPON_50OFF\",\"system\":\"marketing\"}', '二等奖-满100减50优惠券', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `award` VALUES (3, 103, 'user_points', '{\"points\":100}', '三等奖-固定100积分', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `award` VALUES (4, 104, 'random_points', '{\"min\":10,\"max\":100}', '四等奖-随机10~100积分', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `award` VALUES (5, 105, 'random_points', '{\"min\":1,\"max\":10}', '五等奖-随机1~10积分', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `award` VALUES (6, 106, 'user_points', '{\"points\":5}', '兜底奖-5积分(运气值触发)', '2026-05-18 16:00:29', '2026-05-18 16:00:29');

-- ----------------------------
-- Table structure for strategy
-- ----------------------------
DROP TABLE IF EXISTS `strategy`;
CREATE TABLE `strategy`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `strategy_id` bigint NOT NULL COMMENT '策略ID(业务主键)',
  `strategy_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '策略名称',
  `strategy_desc` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '策略描述',
  `total_probability` decimal(10, 6) NOT NULL DEFAULT 1.000000 COMMENT '概率总和, 固定为1',
  `probability_precision` int NOT NULL DEFAULT 10000 COMMENT '概率精度: 1000=千分位, 10000=万分位',
  `free_draw_count` int NOT NULL DEFAULT 0 COMMENT '每人每日免费抽奖次数',
  `points_per_draw` int NOT NULL DEFAULT 0 COMMENT '超出免费次数后每次消耗积分数',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-草稿 1-上线 2-下线',
  `begin_time` datetime NOT NULL COMMENT '活动开始时间',
  `end_time` datetime NOT NULL COMMENT '活动结束时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_strategy_id`(`strategy_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '抽奖策略主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of strategy
-- ----------------------------
INSERT INTO `strategy` VALUES (1, 100001, '618大促幸运转盘', '618年中大促抽奖活动, 每日3次免费, 超出消耗50积分/次', 1.000000, 10000, 3, 50, 1, '2025-06-01 00:00:00', '2027-06-30 23:59:59', '2026-05-18 16:00:29', '2026-05-24 16:26:59');

-- ----------------------------
-- Table structure for strategy_award
-- ----------------------------
DROP TABLE IF EXISTS `strategy_award`;
CREATE TABLE `strategy_award`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `strategy_id` bigint NOT NULL COMMENT '策略ID',
  `award_id` int NOT NULL COMMENT '奖品ID',
  `award_title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '奖品标题(冗余)',
  `award_subtitle` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '奖品副标题',
  `award_type` tinyint NOT NULL COMMENT '1-积分 2-实物 3-内部系统优惠券 4-随机积分 5-运气值兜底奖',
  `award_count` int NOT NULL DEFAULT 0 COMMENT '总库存, -1表示不限库存',
  `award_surplus` int NOT NULL DEFAULT 0 COMMENT '剩余库存',
  `award_rate` decimal(10, 6) NOT NULL DEFAULT 0.000000 COMMENT '中奖概率',
  `sort` int NOT NULL DEFAULT 0 COMMENT '展示排序',
  `rule_models` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联的规则模型ID, 逗号分隔',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_strategy_award`(`strategy_id` ASC, `award_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '策略奖品关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of strategy_award
-- ----------------------------
INSERT INTO `strategy_award` VALUES (1, 100001, 101, 'iPhone 15', '抽满10次解锁', 2, 3, 3, 0.000100, 1, 'rule_lock,rule_weight', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `strategy_award` VALUES (2, 100001, 102, '满100减50优惠券', '内部优惠券', 3, 100, 100, 0.004900, 2, 'rule_weight', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `strategy_award` VALUES (3, 100001, 103, '100积分', '固定积分奖励', 1, -1, -1, 0.095000, 3, 'rule_weight', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `strategy_award` VALUES (4, 100001, 104, '随机10~100积分', '手气不错', 4, -1, -1, 0.200000, 4, 'rule_weight', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `strategy_award` VALUES (5, 100001, 105, '随机1~10积分', '聊胜于无', 4, -1, -1, 0.400000, 5, 'rule_weight', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `strategy_award` VALUES (6, 100001, 106, '5积分(兜底)', '运气值已满触发', 5, -1, -1, 0.300000, 6, 'rule_weight,rule_luck', '2026-05-18 16:00:29', '2026-05-18 16:00:29');

-- ----------------------------
-- Table structure for strategy_rule
-- ----------------------------
DROP TABLE IF EXISTS `strategy_rule`;
CREATE TABLE `strategy_rule`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `strategy_id` bigint NOT NULL COMMENT '策略ID',
  `rule_type` tinyint NOT NULL COMMENT '1-策略级规则 2-奖品级规则',
  `rule_model` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规则模型标识, 如rule_weight, rule_lock, rule_luck',
  `rule_value` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '规则值JSON',
  `rule_desc` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '规则描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_strategy_id`(`strategy_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '策略规则表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of strategy_rule
-- ----------------------------
INSERT INTO `strategy_rule` VALUES (2, 100001, 2, 'rule_lock', '{\"unlock_count\":10,\"locked_award_ids\":[101]}', '抽满10次后解锁一等奖iPhone15', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `strategy_rule` VALUES (3, 100001, 3, 'rule_luck', '{\"luck_threshold\":50,\"luck_award_id\":106,\"luck_increment\":1}', '每次未中大奖运气值+1, 累计50次必得兜底奖', '2026-05-18 16:00:29', '2026-05-21 21:23:46');
INSERT INTO `strategy_rule` VALUES (4, 100001, 1, 'rule_weight', '{\r\n    \"threshold_key\": \"total_spend\",\r\n    \"groups\": [\r\n        {\r\n            \"threshold_value\": 5000,\r\n            \"group_id\": \"weight_group_5000\",\r\n            \"desc\": \"累计消费>=5000, 顶级用户\",\r\n            \"award_rates\": {\r\n                \"101\": 0.010000,\r\n                \"102\": 0.100000,\r\n                \"103\": 0.090000,\r\n                \"104\": 0.200000,\r\n                \"105\": 0.300000,\r\n                \"106\": 0.300000\r\n            }\r\n        },\r\n        {\r\n            \"threshold_value\": 1000,\r\n            \"group_id\": \"weight_group_1000\",\r\n            \"desc\": \"累计消费>=1000, 高消费用户\",\r\n            \"award_rates\": {\r\n                \"101\": 0.001000,\r\n                \"102\": 0.050000,\r\n                \"103\": 0.099000,\r\n                \"104\": 0.200000,\r\n                \"105\": 0.350000,\r\n                \"106\": 0.300000\r\n            }\r\n        }\r\n    ]\r\n}', '权重规则: 根据用户累计消费金额选择不同概率奖池', '2026-05-24 13:02:25', '2026-05-24 13:02:25');

-- ----------------------------
-- Table structure for user_award_record
-- ----------------------------
DROP TABLE IF EXISTS `user_award_record`;
CREATE TABLE `user_award_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户ID',
  `strategy_id` bigint NOT NULL COMMENT '策略ID',
  `award_id` int NOT NULL COMMENT '中奖奖品ID',
  `award_type` tinyint NOT NULL COMMENT '奖品类型(冗余)',
  `award_content` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '实际发放内容, 如88积分或券码',
  `award_state` tinyint NOT NULL DEFAULT 0 COMMENT '0-待发放 1-发放中 2-已发放 3-发放失败',
  `draw_time` datetime NOT NULL COMMENT '抽奖时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_strategy`(`user_id` ASC, `strategy_id` ASC, `draw_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户中奖记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_award_record
-- ----------------------------
INSERT INTO `user_award_record` VALUES (1, 'user_002', 100001, 105, 4, '7积分', 2, '2025-06-05 10:30:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (2, 'user_002', 100001, 104, 4, '58积分', 2, '2025-06-05 10:31:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (3, 'user_002', 100001, 103, 1, '100积分', 2, '2025-06-05 10:32:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (4, 'user_002', 100001, 105, 4, '3积分', 2, '2025-06-05 10:33:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (5, 'user_002', 100001, 105, 4, '9积分', 2, '2025-06-05 10:34:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (6, 'user_003', 100001, 102, 3, 'COUPON_50OFF', 2, '2025-06-03 14:00:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (7, 'user_003', 100001, 104, 4, '42积分', 2, '2025-06-03 14:01:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (8, 'user_003', 100001, 105, 4, '2积分', 2, '2025-06-03 14:02:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (9, 'user_003', 100001, 103, 1, '100积分', 2, '2025-06-03 14:03:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (10, 'user_003', 100001, 105, 4, '6积分', 2, '2025-06-03 14:04:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (11, 'user_003', 100001, 105, 4, '1积分', 2, '2025-06-04 09:00:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (12, 'user_003', 100001, 104, 4, '77积分', 2, '2025-06-04 09:01:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (13, 'user_003', 100001, 105, 4, '4积分', 2, '2025-06-04 09:02:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (14, 'user_003', 100001, 105, 4, '8积分', 2, '2025-06-04 09:03:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (15, 'user_003', 100001, 106, 5, '5积分', 0, '2025-06-04 09:04:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (16, 'user_003', 100001, 101, 2, 'iPhone15-256G', 1, '2025-06-04 09:05:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (17, 'user_003', 100001, 105, 4, '5积分', 2, '2025-06-04 09:06:00', '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_award_record` VALUES (18, 'user_001', 100001, 0, 0, '', 0, '2026-05-24 16:58:25', '2026-05-24 16:58:25', '2026-05-24 16:58:25');

-- ----------------------------
-- Table structure for user_luck_account
-- ----------------------------
DROP TABLE IF EXISTS `user_luck_account`;
CREATE TABLE `user_luck_account`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户ID',
  `strategy_id` bigint NOT NULL COMMENT '策略ID',
  `luck_value` int NOT NULL DEFAULT 0 COMMENT '当前运气值',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_strategy`(`user_id` ASC, `strategy_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户运气值表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_luck_account
-- ----------------------------
INSERT INTO `user_luck_account` VALUES (1, 'user_001', 100001, 0, '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_luck_account` VALUES (2, 'user_002', 100001, 20, '2026-05-18 16:00:29', '2026-05-18 16:00:29');
INSERT INTO `user_luck_account` VALUES (3, 'user_003', 100001, 48, '2026-05-18 16:00:29', '2026-05-18 16:00:29');

SET FOREIGN_KEY_CHECKS = 1;
