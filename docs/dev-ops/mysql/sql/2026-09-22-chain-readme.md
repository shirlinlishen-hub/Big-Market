# 抽奖链路增量迁移

## 运行顺序

1. 在现有单库执行 `2026-09-22-purchase-idempotency.sql`，先处理检查语句列出的重复业务单号。
2. 执行 `2026-09-22-draw-delivery.sql`。这份脚本的 `ALTER TABLE` 只执行一次。
3. 执行 `2026-09-22-raffle-correctness.sql`：先解决检查语句查出的重复规则；脚本为示例策略 100001 配置无限库存奖品 106 作为保底，并将示例权重阈值改成累计抽奖次数 50/10。上线前核对这两个业务阈值。
4. 执行 `2026-09-23-business-entry.sql`。先处理其检查语句列出的全局重复支付单号，再执行 DDL。
5. 暂停购买和抽奖写流量，执行 `2026-09-24-inventory-bucket-outbox.sql`，确认脚本末尾两条库存对账查询均无返回行；部署新代码后再恢复流量，避免迁移快照与旧扣减并发产生库存漂移。
6. 执行 `2026-09-28-outbox-rabbitmq-award-delivery.sql`，增加 Outbox 租约、Inbox 和发放版本字段，并为没有初始 Outbox 的待发放任务补写 `AWARD_DELIVERY_REQUESTED`。脚本可重复执行；末尾前三条检查必须无返回行，最后一条状态汇总用于记录迁移基线。
7. 用 `dev,single` profile 启动；`application-single.yml` 的 JDBC 地址和凭据可通过 `BIG_MARKET_JDBC_URL`、`BIG_MARKET_DB_USER`、`BIG_MARKET_DB_PASSWORD` 覆盖。
8. 配置 `BIG_MARKET_PAYMENT_WEBHOOK_SECRET` 后，由支付系统调用 `/api/v1/internal/payment/events`；签名正文为 `timestamp + "\n" + rawBody` 的十六进制 HMAC-SHA256。
9. 配置 `BIG_MARKET_USER_JWT_SECRET`，用户调用 `/api/v1/raffles/draw` 时携带 Bearer JWT 和 `Idempotency-Key`。用户 ID 只取 JWT `sub`。

支付事件正文示例：

```json
{
  "eventId": "pay-event-20260923-001",
  "eventType": "PAYMENT_SUCCEEDED",
  "paymentOrderNo": "payment-order-001",
  "userId": "user-001",
  "activityId": 20001,
  "skuId": 30001
}
```

退款或撤销保持原 `paymentOrderNo/userId/activityId/skuId`，将 `eventType` 改成 `REFUNDED` 或 `CANCELLED`，并使用新的事件 ID。抽奖正文只需要 `{"activityId":20001}`；策略 ID 从活动配置读取。

## 当前规则

- SKU 配置的 `total_count` 累加到总抽奖机会；`month_count`、`day_count` 是单个用户在活动中的周期上限，重复购买取较高上限，不累加周期上限。
- `activity_order.order_status=3` 表示已确认购买且资格已授予。旧数据的 `0/1/2` 不自动转换，以免把未付款订单误认成资格。
- `sku_rebate_config` 有启用记录时，每笔已确认购买另生成一笔返利任务；返利只增加总机会，不改变周期上限。
- 积分奖在抽奖提交时固定数值，由发放任务写入积分账本和账户。券、实物等奖品进入人工处理状态，需接入对应发放系统后才能自动交付。
- 抽奖处理中超过 10 分钟的订单由补偿任务取消，并恢复总、月、日额度。相同请求号取消后不会再次抽奖，调用方需新请求号。
- 默认与权重奖池使用精确的半开概率区间。策略装配要求每个完整奖池概率合计为 1；概率无法由配置精度精确表示时拒绝启动装配。锁定、运气值和库存裁决在结果事务中执行；有限奖品缺货时只发显式配置的无限库存保底奖。
- 发放任务状态：`0` 待处理、`1` 处理中、`2` 成功、`3` 人工处理、`4` 失败。只有 `3/4` 可以由运营重新入队或人工结案，所有操作写入审计表。
- 有限 SKU 和有限奖品的实时库存以 `inventory_stock_bucket` 为准，原配置表的 `stock_surplus/award_surplus` 只用于迁移初始化。每次成功购买或中奖都会写 `inventory_reservation`。
- 新增有限 SKU、有限奖品或调整库存总量时，必须先通过受控迁移创建或重算 16 个库存桶并完成总量对账，再启用配置；只修改配置表不会自动增加可售库存。
- 购买、退款和抽奖结果事务会同步写 `outbox_event`。当前仍由本地任务发放；接入 MQ 后由 Outbox 发布器发送，不能在业务事务中直接发送消息。
- `outbox_event.status` 使用 `0` 待发布、`1` 已发布、`2` 发布失败终止。发布器只能领取 `AWARD_DELIVERY_REQUESTED`，并通过 `lock_token` 条件更新防止过期发布器覆盖新租约。
- `inbox_event` 保存消费者幂等结果；业务发放、Inbox、积分流水和积分账户必须在同一个 MySQL 事务内提交。

## 上线前依赖

- 当前支付入口使用共享密钥 HMAC 校验，生产环境应由网关限制来源 IP 并定期轮换密钥。券或物流仍没有外部供应商接口，会进入人工发放状态。
- 支付成功、退款和撤销事件都使用同一签名入口。支持 `PAYMENT_SUCCEEDED`、`REFUNDED`、`CANCELLED`；退款会回收未使用额度并记录已经消费的权益暴露量。无限额度退款会暂停可用余额并进入人工审核。
- 发放查询接口为 `/api/v1/awards/{orderId}/delivery`；运营角色可使用 `/api/v1/admin/award-deliveries` 查询、重试和人工结案。运营 JWT 需要包含 `roles: ["operations"]`。
- 分片配置仍是草案：本迁移使用单库本地事务。后续启用分片前，必须设计用户维度共址、DDL、路由和跨表事务，并在真实 MySQL 中做并发与故障恢复测试。
- 需要修改策略概率时，先更新数据库配置并重新装配策略，再开放请求；当前没有在线热切换的版本指针。排除奖池每次按当前数据库配置重新构建，避免沿用旧子奖池缓存。
- 可选的真实 MySQL 库存并发测试使用独立数据库 `big_market_stock_test`。设置 `BIG_MARKET_STOCK_TEST_JDBC_URL`、`BIG_MARKET_STOCK_TEST_DB_USER`、`BIG_MARKET_STOCK_TEST_DB_PASSWORD` 后运行 `MysqlAwardStockConcurrencyTest`；未设置时用例会跳过。测试只创建库存桶表，并创建/删除自己的库存桶记录。
