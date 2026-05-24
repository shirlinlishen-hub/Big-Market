package shirlin.ai.domain.strategy.service.Armory;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.AwardRateRange;
import shirlin.ai.domain.strategy.model.entity.RuleWeightConfigEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.model.valobj.RuleTypeVO;
import shirlin.ai.domain.strategy.service.IStrategyArmory;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;

@Slf4j
@Service
public class StrategyArmory  implements IStrategyArmory {

    @Resource
    private IStrategyRepository strategyRepository;


    /**
     * 策略装配 -- 生成概率区间并缓存到Redis中
     * 调用 -- 当前Strategy上线后/应用启动时预热
     * @param StrategyId -- 抽奖活动
     */
    @Override
    public void assembleLotteryStrategy(Long StrategyId) {

        //1. 查询当前Strategy下的所有奖品
        List<StrategyAwardEntity> awards = strategyRepository.queryStrategyAwardListById(StrategyId);

        //2. 获取概率精度
        StrategyEntity strategy = strategyRepository.queryStrategyById(StrategyId);
        int precision = strategy.getProbabilityPrecision();

        //3. 生成概率区间表
        List<AwardRateRange> rangeTable = new ArrayList<>();
        int currentStart = 0;
        for(StrategyAwardEntity award : awards){
            int rangeWidth = (int)(award.getAwardRate().doubleValue()*precision);
            rangeTable.add(new AwardRateRange(award.getAwardId(),currentStart,currentStart + rangeWidth));
            currentStart += rangeWidth;
        }

        //4. 缓存概率区间表、精度、兜底奖品
        Optional<StrategyAwardEntity> max_award = awards.stream().max(Comparator.comparing(StrategyAwardEntity::getAwardRate));
        strategyRepository.storeStrategyAwardRangeTable(StrategyId, rangeTable);
        strategyRepository.storeStrategyPrecision(StrategyId, precision);
        max_award.ifPresent(award -> strategyRepository.storeStrategyMaxAward(StrategyId, award));

        //5. 初始化各奖品库存到 Redis（awardSurplus 为 null 则视为无限库存，跳过缓存）
        for (StrategyAwardEntity award : awards) {
            strategyRepository.cacheStrategyAwardStock(StrategyId, award.getAwardId(), award.getAwardSurplus());
        }

        //6. 若配置了权重规则，按各分组的概率覆盖构建专属区间表
        StrategyRuleEntity weightRule = strategyRepository.queryStrategyRuleByModel(
                StrategyId, RuleTypeVO.RULEWEIGHT.getRuleModel());

        //存在权重配置
        if (weightRule != null && weightRule.getRuleValue() != null) {
            RuleWeightConfigEntity weightConfig = JSON.parseObject(weightRule.getRuleValue(), RuleWeightConfigEntity.class);
            for (RuleWeightConfigEntity.WeightGroup group : weightConfig.getGroups()) {
                if (group.getAwardRates() == null || group.getAwardRates().isEmpty()) {
                    log.warn("权重分组 awardRates 未配置，跳过 strategyId:{} groupId:{}", StrategyId, group.getGroupId());
                    continue;
                }
                List<AwardRateRange> weightTable = buildRangeTableFromRates(group.getAwardRates(), precision);
                strategyRepository.storeWeightRangeTable(StrategyId, group.getGroupId(), weightTable);
                log.info("权重分组区间表装配完成 strategyId:{} groupId:{} tableSize:{}", StrategyId, group.getGroupId(), weightTable.size());
            }
        }
    }

    private List<AwardRateRange> buildRangeTableFromRates(Map<Integer, BigDecimal> awardRates, int precision) {
        if (awardRates == null || awardRates.isEmpty()) return new ArrayList<>();
        List<AwardRateRange> table = new ArrayList<>();
        int currentStart = 0;
        for (Map.Entry<Integer, BigDecimal> entry : awardRates.entrySet()) {
            int rangeWidth = (int) (entry.getValue().doubleValue() * precision);
            table.add(new AwardRateRange(entry.getKey(), currentStart, currentStart + rangeWidth));
            currentStart += rangeWidth;
        }
        return table;
    }

    /**
     * 抽奖 -- 二分查找命中奖品
     * @param StrategyId
     * @return
     */
    @Override
    public Integer getRandomAwardId(Long StrategyId) {
        //获取精度
        int precision = strategyRepository.getStrategyPrecision(StrategyId);
        //生成[0,precision]间的一个随机数
        int randomValue = new SecureRandom().nextInt(precision);

        //从Redis中取出RangeTbale
        List<AwardRateRange> rangeTable = strategyRepository.getStrategyRangeTable(StrategyId);

        //二分查找
        Integer ans = binarySearch(rangeTable,randomValue);
        if(ans == null || ans.intValue() == -1){
            return strategyRepository.getStrategyMaxAward(StrategyId).getAwardId();
        }
        return ans;
    }

    private Integer binarySearch(List<AwardRateRange> rangeTable,int randomValue){
        int left = 0;
        int right = rangeTable.size()-1;
        while(left<=right){
            int mid = (left+right)>>>1;
            if(rangeTable.get(mid).getRangeEnd()<randomValue){
                left = mid+1;
            }else if(rangeTable.get(mid).getRangeStart()>randomValue){
                right = mid-1;
            }else if(rangeTable.get(mid).getRangeEnd()>=randomValue && rangeTable.get(mid).getRangeStart()<=randomValue){
                return rangeTable.get(mid).getAwardId();
            }
        }
        //兜底
        return -1;
    }

    /**
     * 权重分组抽奖 -- 使用预装配的权重专属区间表
     */
    @Override
    public Integer getRandomAwardId(Long strategyId, String weightGroupId) {
        List<AwardRateRange> weightTable = strategyRepository.getWeightRangeTable(strategyId, weightGroupId);
        if (weightTable == null || weightTable.isEmpty()) {
            log.warn("权重区间表未找到 strategyId:{} groupId:{}，降级走默认抽奖", strategyId, weightGroupId);
            return getRandomAwardId(strategyId);
        }
        int upperBound = weightTable.get(weightTable.size() - 1).getRangeEnd();
        int randomVal = new SecureRandom().nextInt(upperBound);
        Integer result = binarySearch(weightTable, randomVal);
        return (result == null || result == -1) ? getRandomAwardId(strategyId) : result;
    }

    /**
     * 带排除的抽奖 -- 当某些奖品被规则过滤掉，在剩余奖品中计算
     * @param strategyId
     * @param excludeAwardIds
     * @return
     */
    @Override
    public Integer getRandomAwardId(Long strategyId, Set<Integer> excludeAwardIds) {
        // 如果没有排除项, 走常规逻辑
        if (excludeAwardIds == null || excludeAwardIds.isEmpty()) {
            return getRandomAwardId(strategyId);
        }
        // 用排除后的子集生成临时区间表 (可缓存, key加上排除项hash)
        String cacheKey = strategyId + "_exclude_" + excludeAwardIds.hashCode();
        List<AwardRateRange> subTable = strategyRepository.getOrBuildSubRangeTable(
                strategyId, excludeAwardIds, cacheKey
        );
        int subPrecision = subTable.get(subTable.size() - 1).getRangeEnd();
        int randomVal = new SecureRandom().nextInt(subPrecision);
        return binarySearch(subTable, randomVal);
    }

}
