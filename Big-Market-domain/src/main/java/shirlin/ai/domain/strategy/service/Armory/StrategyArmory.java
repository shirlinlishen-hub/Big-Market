package shirlin.ai.domain.strategy.service.Armory;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.AwardRateRange;
import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyEntity;
import shirlin.ai.domain.strategy.service.IStrategyArmory;

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
