package shirlin.ai.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.AwardRateRange;
import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.infrastructure.dao.*;
import shirlin.ai.infrastructure.dao.po.Strategy;
import shirlin.ai.infrastructure.dao.po.StrategyAward;
import shirlin.ai.infrastructure.dao.po.StrategyRule;
import shirlin.ai.infrastructure.redis.IRedisService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class StrategyRepository implements IStrategyRepository {

    // Redis key 前缀
    private static final String RANGE_TABLE_KEY        = "big_market:strategy:range_table:";
    private static final String PRECISION_KEY          = "big_market:strategy:precision:";
    private static final String MAX_AWARD_KEY          = "big_market:strategy:max_award:";
    private static final String SUB_RANGE_TABLE_KEY    = "big_market:strategy:sub_range_table:";
    private static final String WEIGHT_RANGE_TABLE_KEY = "big_market:strategy:weight_range_table:";
    private static final String AWARD_STOCK_KEY        = "big_market:strategy:award:stock:";

    @Resource
    private IStrategyDao strategyDao;

    @Resource
    private IStrategyAwardDao strategyAwardDao;

    @Resource
    private IStrategyRuleDao strategyRuleDao;

    @Resource
    private IUserAwardRecordDao userAwardRecordDao;

    @Resource
    private IUserLuckAccountDao userLuckAccountDao;

    @Resource
    private IRedisService redisService;

    // ---- Strategy / Award 基础查询 ----

    @Override
    public List<StrategyAwardEntity> queryStrategyAwardListById(Long strategyId) {
        List<StrategyAward> pos = strategyAwardDao.selectByStrategyId(strategyId);
        return pos.stream().map(po -> StrategyAwardEntity.builder()
                .strategyId(po.getStrategyId())
                .awardId(po.getAwardId())
                .awardType(po.getAwardType())
                .awardCount(po.getAwardCount())
                .awardSurplus(po.getAwardSurplus())
                .awardRate(po.getAwardRate())
                .sort(po.getSort())
                .ruleModels(po.getRuleModels())
                .build()).collect(Collectors.toList());
    }

    @Override
    public StrategyAwardEntity queryStrategyAward(Long strategyId, Integer awardId) {
        StrategyAward po = strategyAwardDao.selectByStrategyIdAndAwardId(strategyId, awardId);
        if (po == null) return null;
        return StrategyAwardEntity.builder()
                .strategyId(po.getStrategyId())
                .awardId(po.getAwardId())
                .awardType(po.getAwardType())
                .awardCount(po.getAwardCount())
                .awardSurplus(po.getAwardSurplus())
                .awardRate(po.getAwardRate())
                .sort(po.getSort())
                .ruleModels(po.getRuleModels())
                .build();
    }

    @Override
    public StrategyEntity queryStrategyById(Long strategyId) {
        Strategy po = strategyDao.selectByStrategyId(strategyId);
        return StrategyEntity.builder()
                .strategyId(po.getStrategyId())
                .totalProbability(po.getTotalProbability())
                .probabilityPrecision(po.getProbabilityPrecision())
                .freeDrawCount(po.getFreeDrawCount())
                .pointsPerDraw(po.getPointsPerDraw())
                .status(po.getStatus())
                .beginTime(po.getBeginTime())
                .endTime(po.getEndTime())
                .build();
    }

    // ---- 规则配置查询 ----

    @Override
    public StrategyRuleEntity queryStrategyRuleByModel(Long strategyId, String ruleModel) {
        StrategyRule po = strategyRuleDao.selectByStrategyIdAndRuleModel(strategyId, ruleModel);
        if (po == null) return null;
        return StrategyRuleEntity.builder()
                .strategyId(po.getStrategyId())
                .ruleType(po.getRuleType())
                .ruleModel(po.getRuleModel())
                .ruleValue(po.getRuleValue())
                .ruleDesc(po.getRuleDesc())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    // ---- Redis 缓存：概率区间 / 精度 / 兜底奖品 ----

    @Override
    public void storeStrategyAwardRangeTable(Long strategyId, List<AwardRateRange> rangeTable) {
        redisService.setValue(RANGE_TABLE_KEY + strategyId, rangeTable);
    }

    @Override
    public void storeStrategyPrecision(Long strategyId, int precision) {
        redisService.setValue(PRECISION_KEY + strategyId, precision);
    }

    @Override
    public void storeStrategyMaxAward(Long strategyId, StrategyAwardEntity maxAward) {
        redisService.setValue(MAX_AWARD_KEY + strategyId, maxAward);
    }

    @Override
    public int getStrategyPrecision(Long strategyId) {
        return redisService.<Integer>getValue(PRECISION_KEY + strategyId);
    }

    @Override
    public List<AwardRateRange> getStrategyRangeTable(Long strategyId) {
        return redisService.getValue(RANGE_TABLE_KEY + strategyId);
    }

    @Override
    public StrategyAwardEntity getStrategyMaxAward(Long strategyId) {
        return redisService.getValue(MAX_AWARD_KEY + strategyId);
    }

    @Override
    public Integer queryMaxAwardId(Long strategyId) {
        StrategyAwardEntity maxAward = getStrategyMaxAward(strategyId);
        return maxAward != null ? maxAward.getAwardId() : null;
    }

    @Override
    public List<AwardRateRange> getOrBuildSubRangeTable(Long strategyId, Set<Integer> excludeAwardIds, String cacheKey) {
        List<AwardRateRange> cached = redisService.getValue(SUB_RANGE_TABLE_KEY + cacheKey);
        if (cached != null) {
            return cached;
        }

        List<StrategyAwardEntity> awards = queryStrategyAwardListById(strategyId);
        int precision = getStrategyPrecision(strategyId);

        List<StrategyAwardEntity> remaining = awards.stream()
                .filter(a -> !excludeAwardIds.contains(a.getAwardId()))
                .collect(Collectors.toList());

        List<AwardRateRange> subTable = new ArrayList<>();
        int currentStart = 0;
        for (StrategyAwardEntity award : remaining) {
            int rangeWidth = (int) (award.getAwardRate().doubleValue() * precision);
            subTable.add(new AwardRateRange(award.getAwardId(), currentStart, currentStart + rangeWidth));
            currentStart += rangeWidth;
        }

        redisService.setValue(SUB_RANGE_TABLE_KEY + cacheKey, subTable);
        return subTable;
    }

    @Override
    public void storeWeightRangeTable(Long strategyId, String groupId, List<AwardRateRange> table) {
        redisService.setValue(WEIGHT_RANGE_TABLE_KEY + strategyId + ":" + groupId, table);
    }

    @Override
    public List<AwardRateRange> getWeightRangeTable(Long strategyId, String groupId) {
        return redisService.getValue(WEIGHT_RANGE_TABLE_KEY + strategyId + ":" + groupId);
    }

    // ---- 库存操作 ----

    @Override
    public boolean deductStock(Long strategyId, Integer awardId) {
        String key = AWARD_STOCK_KEY + strategyId + ":" + awardId;
        // key 不存在说明该奖品库存未初始化（视为无限库存）
        if (!redisService.isExists(key)) {
            return true;
        }
        long remaining = redisService.decr(key);
        if (remaining < 0) {
            // 补偿回去，防止数值一直下探
            redisService.incrBy(key, 1L);
            return false;
        }
        // TODO: 将 (strategyId, awardId) 推送到延迟队列，异步同步 DB 的 award_surplus 字段
        return true;
    }

    @Override
    public void cacheStrategyAwardStock(Long strategyId, Integer awardId, Integer awardSurplus) {
        // null 或负数（-1 表示无限）均跳过，deductStock 的 isExists 检查会直接放行
        if (awardSurplus == null || awardSurplus < 0) return;
        redisService.setAtomicLong(AWARD_STOCK_KEY + strategyId + ":" + awardId, awardSurplus);
    }

    // ---- 用户状态查询 ----

    @Override
    public int queryUserDrawCount(String userId, Long strategyId) {
        return userAwardRecordDao.countByUserIdAndStrategyId(userId, strategyId);
    }

    @Override
    public int queryUserThresholdValue(String userId, Long strategyId) {

        // 当前以累计抽奖次数作为权重值，可按需替换为积分/会员等级等
        return userAwardRecordDao.countByUserIdAndStrategyId(userId, strategyId);
    }

    @Override
    public int queryUserLuckValue(String userId, Long strategyId) {
        shirlin.ai.infrastructure.dao.po.UserLuckAccount account =
                userLuckAccountDao.selectByUserIdAndStrategyId(userId, strategyId);
        return account != null ? account.getLuckValue() : 0;
    }

    @Override
    public void incrementLuckValue(String userId, Long strategyId) {
        userLuckAccountDao.incrementLuckValue(userId, strategyId, 1);
    }

    @Override
    public void resetUserLuckValue(String userId, Long strategyId) {
        userLuckAccountDao.resetLuckValue(userId, strategyId);
    }


}
