package shirlin.ai.domain.strategy.service.Raffle;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.entity.RuleLockConfigEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.model.valobj.RuleTypeVO;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Factory.DefaultLogicFactory;

/**
 * 默认抽奖策略
 *
 * 前置（责任链）：黑名单 → 权重 → 默认
 * 后置（规则树）：Lock → Stock → 兜底
 */
@Slf4j
@Service
public class DefaultRaffleStrategy extends AbstrackRaffleStrategy {

    @Resource
    private DefaultLogicFactory defaultLogicFactory;

    @Resource
    private IStrategyRepository strategyRepository;



    // =====================================================================
    // 后置规则：规则树  Lock → Stock → 兜底
    // =====================================================================

    @Override
    protected RuleFilterResultEntity doAfterRaffleRuleFilter(RaffleFactorEntity factor, Integer awardId) {

        Long strategyId = factor.getStrategyId();
        String userId   = factor.getUserId();

        // ---- 节点1：Lock（N 次解锁） ----
        StrategyRuleEntity lockRule = strategyRepository.queryStrategyRuleByModel(
                strategyId, RuleTypeVO.RULELOCK.getRuleModel());

        if (lockRule != null) {
            RuleLockConfigEntity lockConfig = JSON.parseObject(lockRule.getRuleValue(), RuleLockConfigEntity.class);
            int userDrawCount = strategyRepository.queryUserDrawCount(userId, strategyId);

            boolean isLocked = lockConfig.getLockedAwardIds() != null
                    && lockConfig.getLockedAwardIds().contains(awardId)
                    && userDrawCount < lockConfig.getUnlockCount();

            if (isLocked) {
                log.info("Lock 拦截 userId:{} awardId:{} drawCount:{}/{}",
                        userId, awardId, userDrawCount, lockConfig.getUnlockCount());
                // 走兜底节点
                return buildFallback(strategyId);
            }
        }

        // ---- 节点2：Stock（库存校验，Redis 原子扣减） ----
        boolean stockOk = strategyRepository.deductStock(strategyId, awardId);
        if (!stockOk) {
            log.info("Stock 耗尽 strategyId:{} awardId:{}", strategyId, awardId);
            // 走兜底节点
            return buildFallback(strategyId);
        }

        // 全部通过，放行
        return RuleFilterResultEntity.builder()
                .type(RuleFilterResultEntity.Type.ALLOW)
                .build();
    }

    /**
     * 兜底节点：取最大概率奖品作为最终安全网
     */
    private RuleFilterResultEntity buildFallback(Long strategyId) {
        Integer fallbackAwardId = strategyRepository.queryMaxAwardId(strategyId);
        return RuleFilterResultEntity.builder()
                .type(RuleFilterResultEntity.Type.TAKE_OVER)
                .awardId(fallbackAwardId)
                .build();
    }

}
