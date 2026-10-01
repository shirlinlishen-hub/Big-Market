# MySQL 库存桶与事务 Outbox 实施计划

## 边界

- 本轮继续使用单库本地事务，不启用 ShardingSphere。
- Redis 只保留活动和策略只读缓存，不再保存 SKU 库存或用户抽奖额度。
- `inventory_stock_bucket` 是有限 SKU、有限奖品实时剩余库存的权威数据源。
- `activity_sku.stock_count`、`strategy_award.award_count` 保留配置总量；原 surplus 字段只作为迁移前快照，不参与扣减决策。
- Outbox 只落库，暂不发布到消息中间件；现有定时发放继续运行。

## 库存桶

- 每个有限库存默认拆成 16 个桶。
- 预占 ID：SKU 使用 `SKU_PURCHASE:{paymentOrderNo}`，奖品使用 `AWARD:{raffleOrderId}`。
- 以预占 ID 哈希确定起始桶，条件更新失败后顺序尝试其余桶。
- 成功扣减后写 `inventory_reservation`，退款根据预占流水恢复原桶。
- 无限库存不生成库存桶；保底奖继续使用 `award_count=-1`。

## Outbox

- 购买成功写 `SKU_QUALIFICATION_GRANTED`。
- 退款/撤销写 `SKU_QUALIFICATION_REVOKED` 或 `SKU_REFUND_MANUAL_REVIEW`。
- 抽奖结果和发放任务提交时写 `AWARD_DELIVERY_REQUESTED`。
- `event_key` 唯一，重复业务请求不会生成重复消息。
- 后续发布器只扫描 `status=0` 的记录；MQ 消费者以 `event_id/event_key` 建 Inbox 幂等。

## 验证

- 库存桶轮询、耗尽、同预占 ID、退款恢复。
- SKU 重复支付不重复预占。
- 奖品并发扣减总量不超过桶库存。
- 业务事务写入对应 Outbox，重复调用不重复写事件。
- 全量定向测试、Maven 编译、Mapper XML 解析和 `git diff --check`。
