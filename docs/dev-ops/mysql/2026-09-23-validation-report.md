# 单库闭环验证记录（2026-09-23）

## 当前环境检查

| 目标 | 结果 |
|---|---|
| 本机 MySQL `127.0.0.1:3306` | TCP 不可达 |
| 本机 Redis `127.0.0.1:6379` | TCP 不可达 |
| 配置 MySQL `159.75.79.79:13306` | TCP 不可达 |
| 配置 Redis `159.75.79.79:16379` | TCP 不可达 |
| MySQL/Redis 命令行 | 未安装 |
| Docker/Podman/WSL 发行版 | 不可用 |

因此本轮没有执行迁移，也没有把单元测试结果描述为真实数据库联调结果。

## 自动化验证

- JDK 21 执行 `mvn -pl Big-Market-app -am test`：共 50 个用例，49 个执行并通过，1 个真实 MySQL 并发用例因未配置专用数据库而跳过。
- MyBatis Mapper XML 全部完成 XML 解析。
- `git diff --check` 通过。

## 获得环境后执行

1. 设置 `BIG_MARKET_JDBC_URL`、`BIG_MARKET_DB_USER`、`BIG_MARKET_DB_PASSWORD`、`BIG_MARKET_REDIS_HOST`、`BIG_MARKET_REDIS_PORT`。
2. 按迁移说明顺序执行五份 SQL，并确认库存桶脚本末尾的两条对账查询均无返回行。
3. 设置不少于 32 个字符的 `BIG_MARKET_PAYMENT_WEBHOOK_SECRET` 和 `BIG_MARKET_USER_JWT_SECRET`，启动 `dev,single` profile。
4. 使用相同支付事件 ID 连续投递两次 `PAYMENT_SUCCEEDED`，确认只有一笔 `activity_order`、一笔 SKU `inventory_reservation`、一次库存桶扣减、一次额度增加和一条资格 Outbox。
5. 使用相同 JWT 用户并发提交不同 `Idempotency-Key`，检查所有库存桶 `stock_surplus >= 0`，中奖记录、库存预占、发放任务和发放 Outbox 一一对应。
6. 将有限奖库存设置为 0，确认结果只转为 `rule_fallback` 指定的无限库存奖品。
7. 制造积分账本写入异常，确认任务按退避时间重试，达到上限后进入 `status=4`；再通过运营 API 重新入队或人工结案并检查审计表。
8. 在抽奖订单进入 `status=0` 后终止进程，等待超过 10 分钟并重启，确认恢复任务把订单改为取消且总/月/日使用量只恢复一次。

## 验收 SQL

```sql
SELECT payment_order_no, COUNT(*) FROM payment_event GROUP BY payment_order_no;
SELECT out_business_no, COUNT(*) FROM activity_order GROUP BY out_business_no HAVING COUNT(*) > 1;
SELECT inventory_type, inventory_key, SUM(stock_count), SUM(stock_surplus)
FROM inventory_stock_bucket GROUP BY inventory_type, inventory_key
HAVING SUM(stock_surplus) < 0 OR SUM(stock_surplus) > SUM(stock_count);
SELECT reservation_id, COUNT(*) FROM inventory_reservation
GROUP BY reservation_id HAVING COUNT(*) > 1;
SELECT raffle_order_id, COUNT(*) FROM user_award_record GROUP BY raffle_order_id HAVING COUNT(*) > 1;
SELECT order_id, COUNT(*) FROM award_delivery_task GROUP BY order_id HAVING COUNT(*) > 1;
SELECT event_key, COUNT(*) FROM outbox_event GROUP BY event_key HAVING COUNT(*) > 1;
SELECT event_type, status, COUNT(*) FROM outbox_event GROUP BY event_type, status;
SELECT status, COUNT(*) FROM award_delivery_task GROUP BY status;
SELECT status, COUNT(*) FROM raffle_order GROUP BY status;
```

## RabbitMQ 发放联调准备（2026-10-01）

### 本机环境

| 目标 | 结果 |
|---|---|
| 本机 MySQL `127.0.0.1:3306`、`127.0.0.1:13306` | TCP 不可达 |
| 本机 RabbitMQ `127.0.0.1:5672`、管理端口 `15672` | TCP 不可达 |
| 本机 Redis `127.0.0.1:6379`、`127.0.0.1:16379` | TCP 不可达 |
| Docker、Podman、MySQL CLI、RabbitMQ CLI | 未发现可执行程序 |

因此本轮无法执行真实 MySQL 与 RabbitMQ 联调，也没有把环境跳过记录成联调通过。

### 已完成的可执行准备

- 新增 `RabbitAwardDeliveryIntegrationTest`，只在 `BIG_MARKET_REAL_INTEGRATION=true` 时连接真实 MySQL/RabbitMQ。
- 测试覆盖五个故障窗口：重复发布、Confirm 后跳过 Outbox 标记、事务提交后 ACK 丢失、同事件 ID 协议冲突进入死信、不同人工重试事件指向同一订单。
- 未开启环境开关时，测试完成编译并明确显示 5 个用例全部跳过。
- 单元与上下文全量回归在加入联调测试前为 96 个用例：95 个通过，1 个既有真实 MySQL 用例因环境缺失跳过。

### 获得环境后的命令

```powershell
$env:BIG_MARKET_REAL_INTEGRATION='true'
$env:BIG_MARKET_JDBC_URL='jdbc:mysql://127.0.0.1:13306/Big-Market?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:BIG_MARKET_DB_USER='root'
$env:BIG_MARKET_DB_PASSWORD='replace-me'
$env:BIG_MARKET_RABBIT_HOST='127.0.0.1'
$env:BIG_MARKET_RABBIT_PORT='5672'
$env:BIG_MARKET_RABBIT_USERNAME='guest'
$env:BIG_MARKET_RABBIT_PASSWORD='guest'
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn -pl Big-Market-app -am '-Dtest=RabbitAwardDeliveryIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

执行后需补录 MySQL 版本、RabbitMQ 版本、执行时间、5 个用例结果和失败日志位置。
