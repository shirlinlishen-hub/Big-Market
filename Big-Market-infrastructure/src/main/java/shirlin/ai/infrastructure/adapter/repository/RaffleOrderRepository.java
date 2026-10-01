package shirlin.ai.infrastructure.adapter.repository;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.strategy.adapter.repository.IRaffleOrderRepository;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.DrawOrderEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
import shirlin.ai.domain.strategy.model.entity.RuleLockConfigEntity;
import shirlin.ai.domain.strategy.model.entity.RuleLuckConfigEntity;
import shirlin.ai.domain.strategy.service.Rule.PostDrawRulePolicy;
import shirlin.ai.infrastructure.dao.*;
import shirlin.ai.infrastructure.dao.po.*;
import shirlin.ai.types.Tool.SnowflakeIdGenerator;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@Repository
public class RaffleOrderRepository implements IRaffleOrderRepository {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Resource private IDrawOrderDao drawOrderDao;
    @Resource private IActivityOrderDao activityOrderDao;
    @Resource private IActivityAccountDao accountDao;
    @Resource private IUsagePeriodDao usagePeriodDao;
    @Resource private IStrategyAwardDao strategyAwardDao;
    @Resource private IUserAwardRecordDao awardRecordDao;
    @Resource private IAwardDeliveryTaskDao deliveryTaskDao;
    @Resource private IAwardDao awardDao;
    @Resource private IStrategyRepository strategyRepository;
    @Resource private IStrategyRuleDao strategyRuleDao;
    @Resource private IUserLuckAccountDao luckAccountDao;
    @Resource private DrawRecoveryService drawRecoveryService;
    @Resource private SnowflakeIdGenerator idGenerator;
    @Resource private MysqlInventoryBucketService inventoryBucketService;
    @Resource private TransactionalOutboxService outboxService;

    @Override
    public DrawOrderEntity findByRequest(String userId, String requestNo) {
        return toEntity(drawOrderDao.selectByUserIdAndRequestNo(userId, requestNo), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DrawOrderEntity reserveDraw(ActivityFactorEntity factor) {
        DrawOrder existing = drawOrderDao.selectByUserIdAndRequestNo(
                factor.getUserId(), factor.getOutBusinessNo());
        if (existing != null) return toEntity(existing, false);
        String userId = factor.getUserId();
        Long activityId = factor.getActivityId();
        if (activityOrderDao.countGrantedOrders(userId, activityId) == 0) {
            throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getCode());
        }
        ActivityAccount account = accountDao.selectByUserIdAndActivityId(userId, activityId);
        if (account == null || account.getTotalCountSurplus() == null
                || account.getMonthCount() == null || account.getDayCount() == null) {
            throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getCode());
        }
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        String month = today.toString().substring(0, 7);
        String day = today.toString();
        usagePeriodDao.insertIfAbsent(userId, activityId, "M", month);
        usagePeriodDao.insertIfAbsent(userId, activityId, "D", day);
        if (usagePeriodDao.incrementWithinLimit(userId, activityId, "M", month,
                account.getMonthCount()) != 1
                || usagePeriodDao.incrementWithinLimit(userId, activityId, "D", day,
                account.getDayCount()) != 1
                || accountDao.deductTotalSurplus(userId, activityId) != 1) {
            throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getCode());
        }
        DrawOrder order = new DrawOrder();
        order.setOrderId(String.valueOf(idGenerator.nextId()));
        order.setUserId(userId);
        order.setActivityId(activityId);
        order.setStrategyId(factor.getStrategyId());
        order.setRequestNo(factor.getOutBusinessNo());
        order.setStatus(0);
        order.setDrawDay(Date.valueOf(today));
        order.setDrawMonth(month);
        drawOrderDao.insert(order);
        return toEntity(order, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RaffleResultEntity completeDraw(DrawOrderEntity reserved, RaffleResultEntity candidate) {
        DrawOrder order = drawOrderDao.selectForUpdate(reserved.getUserId(), reserved.getOrderId());
        if (order == null) throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode());
        if (order.getStatus() == 1) {
            return RaffleResultEntity.builder()
                    .awardId(order.getAwardId()).awardType(order.getAwardType()).build();
        }
        if (order.getStatus() != 0 || candidate == null || candidate.getAwardId() == null) {
            throw new AppException(ResponseCode.UN_ERROR.getCode());
        }
        Long strategyId = order.getStrategyId();
        int fallbackId = strategyRepository.queryFallbackAwardId(strategyId);
        StrategyRule lockRule = strategyRuleDao.selectByStrategyIdAndRuleModel(strategyId, "rule_lock");
        StrategyRule luckRule = strategyRuleDao.selectByStrategyIdAndRuleModel(strategyId, "rule_luck");
        RuleLockConfigEntity lockConfig = lockRule == null ? null
                : JSON.parseObject(lockRule.getRuleValue(), RuleLockConfigEntity.class);
        RuleLuckConfigEntity luckConfig = luckRule == null ? null
                : JSON.parseObject(luckRule.getRuleValue(), RuleLuckConfigEntity.class);
        if ((lockRule != null && lockConfig == null) || (luckRule != null && luckConfig == null)) {
            throw new IllegalArgumentException("Post-draw rule configuration is empty");
        }
        PostDrawRulePolicy.validate(lockConfig, luckConfig, fallbackId);
        int drawCount = 0;
        int currentLuck = 0;
        if (lockConfig != null || luckConfig != null) {
            luckAccountDao.insertIfAbsent(order.getUserId(), strategyId);
            UserLuckAccount state = luckAccountDao.selectForUpdate(order.getUserId(), strategyId);
            if (state == null || state.getLuckValue() == null) {
                throw new IllegalStateException("User luck account is missing");
            }
            currentLuck = state.getLuckValue();
            drawCount = awardRecordDao.countByUserIdAndStrategyId(order.getUserId(), strategyId);
        }
        int proposedAwardId = PostDrawRulePolicy.choose(candidate.getAwardId(), fallbackId,
                lockConfig, drawCount, luckConfig, currentLuck);
        StrategyAward award = reserveAwardOrFallback(
                strategyId, proposedAwardId, fallbackId, order.getOrderId());
        if (drawOrderDao.complete(order.getUserId(), order.getOrderId(),
                award.getAwardId(), award.getAwardType()) != 1) {
            throw new AppException(ResponseCode.UN_ERROR.getCode());
        }
        Award awardConfig = awardDao.selectByAwardId(award.getAwardId());
        String awardValue = AwardPayloadResolver.resolve(awardConfig);
        UserAwardRecord record = UserAwardRecord.builder()
                .userId(order.getUserId()).strategyId(order.getStrategyId())
                .raffleOrderId(order.getOrderId()).awardId(award.getAwardId())
                .awardType(award.getAwardType()).awardContent(awardValue)
                .awardState(0).drawTime(new java.util.Date()).build();
        awardRecordDao.insert(record);
        AwardDeliveryTask task = new AwardDeliveryTask();
        task.setOrderId(order.getOrderId());
        task.setUserId(order.getUserId());
        task.setAwardId(award.getAwardId());
        task.setAwardType(award.getAwardType());
        task.setAwardKey(awardConfig.getAwardKey());
        task.setAwardValue(awardValue);
        deliveryTaskDao.insert(task);
        if (luckConfig != null && luckAccountDao.setLuckValue(order.getUserId(), strategyId,
                PostDrawRulePolicy.nextLuck(award.getAwardId(), luckConfig, currentLuck)) != 1) {
            throw new IllegalStateException("Failed to update luck value");
        }
        outboxService.append("AWARD_DELIVERY_REQUESTED",
                "award-delivery:" + order.getOrderId() + ":v0",
                order.getOrderId(), order.getUserId(), Map.of(
                        "orderId", order.getOrderId(),
                        "userId", order.getUserId()));
        return RaffleResultEntity.builder()
                .awardId(award.getAwardId()).awardType(award.getAwardType())
                .sort(award.getSort()).build();
    }

    private StrategyAward reserveAwardOrFallback(Long strategyId, Integer candidateId,
                                                 Integer fallbackId, String raffleOrderId) {
        StrategyAward candidate = strategyAwardDao.selectByStrategyIdAndAwardId(strategyId, candidateId);
        if (candidate == null) {
            throw new IllegalStateException("Selected award is not configured in the strategy");
        }
        if (candidate.getAwardCount() == null || candidate.getAwardCount() < -1) {
            throw new IllegalStateException("Candidate award stock is inconsistent");
        }
        if (candidate.getAwardCount() == -1) {
            if (candidate.getAwardSurplus() != -1) {
                throw new IllegalStateException("Unlimited award stock is inconsistent");
            }
            if (!inventoryBucketService.reserveAward(strategyId, candidateId,
                    raffleOrderId, candidate.getAwardCount())) {
                throw new IllegalStateException("Unlimited award reservation is unavailable");
            }
            return candidate;
        }
        if (inventoryBucketService.reserveAward(strategyId, candidateId,
                raffleOrderId, candidate.getAwardCount())) {
            return candidate;
        }
        StrategyAward fallback = strategyAwardDao.selectByStrategyIdAndAwardId(strategyId, fallbackId);
        if (fallback == null || !Integer.valueOf(-1).equals(fallback.getAwardCount())
                || !Integer.valueOf(-1).equals(fallback.getAwardSurplus())) {
            throw new IllegalStateException("Configured fallback award is unavailable");
        }
        if (!inventoryBucketService.reserveAward(strategyId, fallbackId,
                raffleOrderId, fallback.getAwardCount())) {
            throw new IllegalStateException("Fallback award reservation is unavailable");
        }
        return fallback;
    }

    private DrawOrderEntity toEntity(DrawOrder order, boolean newlyCreated) {
        if (order == null) return null;
        return DrawOrderEntity.builder()
                .orderId(order.getOrderId()).userId(order.getUserId())
                .activityId(order.getActivityId()).strategyId(order.getStrategyId())
                .requestNo(order.getRequestNo()).status(order.getStatus())
                .awardId(order.getAwardId()).awardType(order.getAwardType())
                .newlyCreated(newlyCreated).build();
    }

    @Override
    public void cancelFailedDraw(DrawOrderEntity order) {
        drawRecoveryService.cancelTimedOut(order.getUserId(), order.getOrderId());
    }
}
