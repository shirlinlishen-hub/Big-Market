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
import shirlin.ai.domain.strategy.service.Rule.Tree.Factory.DefaultLogicFactory;
import shirlin.ai.domain.strategy.service.Rule.Tree.IStrategyLogicFilterService;

import java.util.Collections;

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
    // 前置规则：责任链
    // =====================================================================

    @Override
    protected RuleFilterResultEntity doBeforeRaffleRuleFilter(RaffleFactorEntity factor) {

        // 节点1：黑名单规则 — 命中则直接接管，不再往下走
        IStrategyLogicFilterService blacklistFilter =
                defaultLogicFactory.getFilter(RuleTypeVO.RULEBLACKLIST.getRuleBeanName());
        RuleFilterResultEntity blacklistResult = blacklistFilter.filter(factor);
        if (RuleFilterResultEntity.Type.TAKE_OVER.equals(blacklistResult.getType())) {
            return blacklistResult;
        }

        // 节点2：权重规则 — 决定本次抽奖排除哪些奖品
        IStrategyLogicFilterService weightFilter =
                defaultLogicFactory.getFilter(RuleTypeVO.RULEWEIGHT.getRuleBeanName());
        RuleFilterResultEntity weightResult = weightFilter.filter(factor);

        // 节点3：默认规则 — 透传权重规则的排除集（未命中任何权重阈值时集合为空）
        return RuleFilterResultEntity.builder()
                .type(RuleFilterResultEntity.Type.ALLOW)
                .excludeAwardIds(
                        weightResult.getExcludeAwardIds() != null
                                ? weightResult.getExcludeAwardIds()
                                : Collections.emptySet())
                .build();
    }

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
