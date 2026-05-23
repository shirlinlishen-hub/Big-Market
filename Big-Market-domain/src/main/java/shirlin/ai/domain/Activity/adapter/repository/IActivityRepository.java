package shirlin.ai.domain.Activity.adapter.repository;

public interface IActivityRepository {

    // ---- 活动 SKU 库存（责任链 Node2） ----

    /**
     * 扣减活动 SKU 库存
     * 流程：decr → <0 则补偿返回 false；≥0 则 setnx 锁定序号 + 写延迟队列
     */
    boolean deductActivitySkuStock(Long strategyId);

    /**
     * 活动上线时将 SKU 总库存写入 Redis
     */
    void cacheActivitySkuStock(Long strategyId, Long totalStock);

    // ---- 参与订单（Phase 2 事务内） ----

    /**
     * 创建用户参与订单（state=0，awardId=0 占位）
     * @return 生成的订单 ID
     */
    Long createUserRaffleOrder(String userId, Long strategyId);

    /**
     * 抽奖完成后回填订单 awardId 并将状态改为 1（completed）
     */
    void updateUserRaffleOrder(Long orderId, Integer awardId, Integer awardType);

    // ---- 用户活动额度扣减（activity_account，Phase 2 事务内） ----

    /**
     * 扣减总剩余次数（activity_account.totalCountSurplus--，乐观更新）
     * @return true 成功，false 额度不足
     */
    boolean deductUserTotalQuota(String userId, Long strategyId);

    /**
     * 扣减月剩余次数
     * Redis 快速拦截（首次从 activity_account.monthCount 初始化，TTL 到月底）
     * + DB deductMonthSurplus 兜底
     * @return true 成功，false 月额度已耗尽
     */
    boolean deductUserMonthlyQuota(String userId, Long strategyId);

    /**
     * 扣减日剩余次数
     * Redis 快速拦截（首次从 activity_account.dayCount 初始化，TTL 到当天 23:59:59）
     * + DB deductDaySurplus 兜底
     * @return true 成功，false 日额度已耗尽
     */
    boolean deductUserDailyQuota(String userId, Long strategyId);
}
