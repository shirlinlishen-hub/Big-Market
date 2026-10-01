# 抽奖正确性优化实施方案

## 目标与边界

本轮只修改策略装配、抽奖后规则与奖品库存裁决。购买 SKU 后授额、每日/月度额度、积分发放的业务语义保持现状。开发运行仍以单库本地事务为边界；分库分表和消息队列另行设计。

完成标准：有效配置下，每个随机票号恰好对应一个奖品；无效配置不能上线；抽中无库存奖品时只能转向显式保底奖；并发抽奖不会把有限库存扣成负数；所有已启用的抽奖后规则都能在唯一的主路径执行。

## 当前代码问题

1. `StrategyArmory` 和 `StrategyRepository.getOrBuildSubRangeTable` 用 `double * precision` 截断宽度，并把相邻区间都当成闭区间。边界重叠，零宽区间也可能被命中；默认、权重、排除奖池没有统一校验。
2. 兜底奖由“最高概率”推断。`RaffleOrderRepository.completeDraw` 在缺货时遍历其他可用奖品，相当于改变配置概率；规则接管与缺货处理还可能使用不同兜底来源。
3. `DefaultRaffleStrategy` 内联了锁定规则，`RuleLockFilterNode`、`RuleLuckFilterNode` 和 `RuleAwardStockFilterNode` 不在主路径。示例数据配置了 `rule_luck`，但主流程不会生效。
4. `StrategyArmory` 预热 Redis 奖品库存，实际抽奖却在 MySQL 扣减；旧的 Redis `deductStock` 未被调用。两份库存缺少同步协议。
5. 示例权重规则声明 `threshold_key=total_spend`，`StrategyRepository.queryUserThresholdValue` 实际返回累计抽奖次数。现有订单没有可信的实付金额，两个概念不能混用。

## 设计决定

### 概率与奖池

- 建立一个 `ProbabilityTableBuilder`，供默认、权重和排除奖池共用。所有区间采用 `[start, end)`；抽样只在 `[0, totalWidth)` 内进行，按 `ticket < end` 查找。
- 用 `BigDecimal` 乘以 `probability_precision` 并调用 `intValueExact()`。无法精确表示的概率直接拒绝配置，不再截断。默认和权重池每项概率必须在 `[0,1]`，合计必须等于 `1`；奖品 ID 唯一且属于该策略。零概率奖品不产生区间，至少要有一个正概率奖品。
- 排除奖池从已经校验的默认奖池构建，剩余概率不必重新相加为 1，但总宽度必须大于 0；排除全部奖品时明确失败，不回退到完整奖池。权重组按奖品 ID 排序，使相同配置得到稳定表。
- 权重规则只接受代码确实支持的阈值类型；默认建议把示例规则迁移为 `draw_count` 并设置对应阈值。若产品必须使用 `total_spend`，先接入可信订单金额并重新定义累计范围，不能继续用抽奖次数冒充。
- 权重组阈值要非负且互不重复，装配时按阈值降序排列；不能依赖 JSON 中 `groups` 的原始顺序来决定用户命中的分组。
- 缓存的区间数据换版本键或在上线时覆盖并校验，防止旧闭区间缓存被新代码误读。装配失败时不发布半成品缓存。

### 显式保底与库存

- 在 `strategy_rule` 增加 `rule_fallback`，规则值形如 `{"award_id":106}`。启动装配时检查奖品属于该策略、配置可解析、`award_count=-1` 且 `award_surplus=-1`。缺少合法保底奖的策略拒绝装配。迁移前先检查重复 `(strategy_id, rule_model)`，再加唯一索引，避免同一规则被读取成多条。
- 黑名单接管、锁定奖品和缺货都使用同一个保底奖 ID。有限奖库存继续使用 MySQL 条件更新 `award_surplus > 0`，与抽奖订单结果、中奖记录和发放任务在同一事务中提交；无限库存奖不执行无意义扣减。
- 抽中的有限奖缺货时只尝试保底奖；保底奖异常则回滚结果事务，不遍历其他奖品。调用方随后以独立事务取消仍处于 `status=0` 的抽奖订单并恢复用户总/月/日额度；若进程在此间崩溃，现有超时任务负责最终补偿。取消与完成都先锁订单并检查状态，避免重复恢复。

### 唯一后置规则路径

- 前置责任链仍只做黑名单、权重。抽样后不再在 `DefaultRaffleStrategy` 内联锁定判断。
- 抽奖结果事务内，由单个 `PostDrawRulePolicy` 按 **锁定 → 运气值 → 库存/保底** 顺序决策；仓储负责读取/锁定用户状态及落库，规则本身保持可单测。规则配置缺失则跳过，配置存在但无效则拒绝装配或抽奖，不静默忽略。
- 移除没有进入主路径的旧规则树工厂和节点，避免存在两套名字相同、语义不同的规则实现。示例中的 `rule_luck` 不再是备用节点。
- 对运气值，默认口径是“完成非运气保底奖的抽奖后增加 `luck_increment`，命中 `luck_award_id` 后归零”；第 `luck_threshold` 次抽奖强制命中保底。`user_luck_account` 按 `(user_id,strategy_id)` 建行并加锁，与最终奖品、中奖记录一起提交。若业务要求“中指定大奖时清零”，扩展配置 `reset_award_ids` 并调整此规则，不在代码中猜测大奖 ID。

## 文件与实施顺序

| 阶段 | 主要文件 | 可验收结果 |
|---|---|
| 1. 概率表 | 新增 `Big-Market-domain/.../strategy/service/Armory/ProbabilityTableBuilder.java`；修改 `StrategyArmory.java`、`StrategyRepository.java`、`AwardRateRange.java` | 默认、权重、排除奖池采用相同校验和半开区间；无效配置无法缓存 |
| 2. 保底与库存 | 修改 `IStrategyRepository.java`、`StrategyRepository.java`、`RaffleOrderRepository.java`、`IStrategyAwardDao.java`、`StrategyAwardMapper.xml`；新增 `docs/dev-ops/mysql/sql/2026-09-22-fallback-rule.sql` | 显式保底；有限库存原子扣减；缺货不会改派任意其他奖 |
| 3. 规则主路径 | 新增 `PostDrawRulePolicy.java`；修改 `DefaultRaffleStrategy.java`、`RaffleOrderRepository.java`、`IUserLuckAccountDao.java`、`UserLuckAccountMapper.xml`；删除不再调用的后置节点/工厂 | 锁定、运气值、库存只在一个流程执行，状态与中奖结果同事务 |
| 4. 验证与文档 | 新增概率、规则和库存用例；更新运行说明 | 确定性概率测试、零库存测试、MySQL 并发扣减测试和故障回滚测试通过 |

## 测试矩阵

- 概率：精度 10000、比例 `0.0001/0.9999`；枚举全部 10000 个票号，两个奖品分别命中 1/9999 次，且无遗漏或重复。
- 非法配置：总和不等于 1、负概率、重复奖品 ID、权重组引用不存在奖品、非精度整数倍、所有奖被排除、保底缺失或有限库存，均得到明确异常且不写入缓存。
- 规则：前置黑名单、权重分组、锁定奖、运气阈值 49→50、保底归零和同请求重复调用；验证每条规则只触发一次。
- 库存：零库存的候选奖只发显式保底；在真实 MySQL 对库存为 10 的有限奖并发提交 100 个不同抽奖请求，最多 10 个得到该奖、其余得到保底，剩余库存不小于 0；相同请求并发不生成重复中奖/发放任务。
- 回滚：发放任务插入失败时，有限奖库存扣减、运气值改变和中奖记录都回滚；超时补偿仍按原抽奖订单恢复用户抽奖额度。

## 验证门槛

先针对每项行为写失败测试，再修改实现；运行相关单元测试、离线 Maven 编译、XML 解析和 `git diff --check`。并发库存与事务回滚必须在真实 MySQL 环境验证；没有数据库环境时不得把单元测试通过描述为已验证高并发正确性。
