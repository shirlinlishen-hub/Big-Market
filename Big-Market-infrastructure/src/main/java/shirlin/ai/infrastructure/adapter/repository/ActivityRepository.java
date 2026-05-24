package shirlin.ai.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.springframework.stereotype.Repository;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.model.entity.ActivitySkuEntity;
import shirlin.ai.infrastructure.dao.IActivityAccountDao;
import shirlin.ai.infrastructure.dao.IActivityDao;
import shirlin.ai.infrastructure.dao.IActivitySkuDao;
import shirlin.ai.infrastructure.dao.IUserAwardRecordDao;
import shirlin.ai.infrastructure.dao.po.Activity;
import shirlin.ai.infrastructure.dao.po.ActivityAccount;
import shirlin.ai.infrastructure.dao.po.ActivitySku;
import shirlin.ai.infrastructure.dao.po.UserAwardRecord;
import shirlin.ai.infrastructure.redis.IRedisService;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Repository
public class ActivityRepository implements IActivityRepository {

    private static final String ACTIVITY_KEY = "big_market:activity:activity";
    private static final String SKU_STOCK_KEY       = "big_market:activity:sku:stock:";
    private static final String SKU_STOCK_LOCK_KEY  = "big_market:activity:sku:stock:lock:";
    private static final String SKU_STOCK_QUEUE_KEY = "big_market:activity:sku:stock:queue";
    private static final String MONTHLY_QUOTA_KEY   = "big_market:user:quota:monthly:";
    private static final String DAILY_QUOTA_KEY     = "big_market:user:quota:daily:";

    @Resource
    private IActivityDao activityDao;

    @Resource
    private IActivitySkuDao activitySkuDao;

    @Resource
    private IActivityAccountDao activityAccountDao;

    @Resource
    private IUserAwardRecordDao userAwardRecordDao;

    @Resource
    private IRedisService redisService;

    private Long queryActivityId(Long activityId) {
        Activity activity = activityDao.selectByStrategyId(activityId);
        if (activity == null) {
            throw new AppException(ResponseCode.ACTIVITY_NOT_EXISTS.getInfo());
        }
        return activity.getActivityId();
    }

    @Override
    public boolean deductActivitySkuStock(Long activityId,Long skuId) {

        String stockKey = SKU_STOCK_KEY + activityId + skuId;

        if (!redisService.isExists(stockKey)) {
            return true;
        }

        long remaining = redisService.decr(stockKey);
        if (remaining < 0) {
            redisService.setAtomicLong(stockKey, 0L);
            return false;
        }

        String lockKey = SKU_STOCK_LOCK_KEY + activityId + skuId + ":" + remaining;
        boolean locked = redisService.setNx(lockKey);
        if (!locked) {
            redisService.incrBy(stockKey, 1L);
            return false;
        }

        RBlockingQueue<Long> blockingQueue =
                redisService.getBlockingQueue(SKU_STOCK_QUEUE_KEY + activityId + skuId);
        RDelayedQueue<Long> delayedQueue =
                redisService.getDelayedQueue(blockingQueue);
        delayedQueue.offer(activityId, 3, TimeUnit.SECONDS);

        return true;
    }

    @Override
    public void cacheActivitySkuStock(Long activityId,Long skuId, Long totalStock) {
        redisService.setAtomicLong(SKU_STOCK_KEY + activityId + skuId, totalStock);
    }


    @Override
    public void cacheActivity(ActivityEntity activity) {
        redisService.setValue(ACTIVITY_KEY + activity.getActivityId(), activity);
    }

    @Override
    public ActivityEntity cacheGetActivity(Long activityId) {
        return redisService.getValue(ACTIVITY_KEY + activityId);

    }


    @Override
    public Long createUserRaffleOrder(String userId, Long strategyId) {
        UserAwardRecord po = new UserAwardRecord();
        po.setUserId(userId);
        po.setStrategyId(strategyId);
        po.setAwardId(0);
        po.setAwardType(0);
        po.setAwardContent("");
        po.setAwardState(0);
        po.setDrawTime(new Date());
        userAwardRecordDao.insertOrder(po);
        return po.getId();
    }

    @Override
    public void updateUserRaffleOrder(Long orderId, Integer awardId, Integer awardType) {
        userAwardRecordDao.updateOrderResult(orderId, awardId, awardType);
    }

    @Override
    public boolean deductUserTotalQuota(String userId, Long activityId) {
        int affected = activityAccountDao.deductTotalSurplus(userId, activityId);
        return affected > 0;
    }

    @Override
    public boolean deductUserMonthlyQuota(String userId, Long activityId) {

        String yearMonth = YearMonth.now().toString();
        String key = MONTHLY_QUOTA_KEY + userId + ":" + activityId + ":" + yearMonth;

        if (!redisService.isExists(key)) {
            ActivityAccount account = activityAccountDao.selectByUserIdAndActivityId(userId, activityId);
            if (account == null) {
                throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getInfo());
            }
            int monthLimit = account.getMonthCount() != null ? account.getMonthCount() : 30;
            redisService.setAtomicLong(key, monthLimit);
            long ttl = ChronoUnit.SECONDS.between(
                    LocalDateTime.now(),
                    YearMonth.now().atEndOfMonth().atTime(23, 59, 59));
            redisService.setExpire(key, ttl, TimeUnit.SECONDS);
        }

        long remaining = redisService.decr(key);
        if (remaining < 0) {
            redisService.incrBy(key, 1L);
            return false;
        }

        int affected = activityAccountDao.deductMonthSurplus(userId, activityId);
        if (affected == 0) {
            redisService.incrBy(key, 1L);
            return false;
        }
        return true;
    }

    @Override
    public boolean deductUserDailyQuota(String userId, Long activityId) {

        String today = LocalDate.now().toString();
        String key = DAILY_QUOTA_KEY + userId + ":" + activityId + ":" + today;

        if (!redisService.isExists(key)) {
            ActivityAccount account = activityAccountDao.selectByUserIdAndActivityId(userId, activityId);
            if (account == null) {
                throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getInfo());
            }
            int dayLimit = account.getDayCount() != null ? account.getDayCount() : 5;
            redisService.setAtomicLong(key, dayLimit);
            LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);
            long ttl = ChronoUnit.SECONDS.between(LocalDateTime.now(), endOfDay);
            redisService.setExpire(key, ttl, TimeUnit.SECONDS);
        }

        long remaining = redisService.decr(key);
        if (remaining < 0) {
            redisService.incrBy(key, 1L);
            return false;
        }

        int affected = activityAccountDao.deductDaySurplus(userId, activityId);
        if (affected == 0) {
            redisService.incrBy(key, 1L);
            return false;
        }
        return true;
    }

    @Override
    public ActivityEntity queryActivityById(Long activityId) {
        Activity res = activityDao.selectByActivityId(activityId);
        return    ActivityEntity.builder()
                      .activityId(res.getActivityId())
                      .strategyId(res.getStrategyId())
                      .status(res.getStatus())
                      .beginTime(res.getBeginTime())
                      .endTime(res.getEndTime())
                      .build();
    }

    @Override
    public List<ActivitySkuEntity> querySkuByActivityId(Long activityId) {
        List<ActivitySku> res = activitySkuDao.selectByActivityId(activityId);
        List<ActivitySkuEntity> ans = new ArrayList<>();
        for (ActivitySku sku : res) {
            ActivitySkuEntity req = ActivitySkuEntity.builder()
                        .skuId(sku.getSkuId())
                        .activityId(sku.getActivityId())
                        .skuType(sku.getSkuType())
                        .pointsCost(sku.getPointsCost())
                        .activityCountId(sku.getActivityCountId())
                        .stockCount(sku.getStockCount())
                        .stockSurplus(sku.getStockSurplus())
                        .status(sku.getStatus())
                        .build();
            ans.add(req);
        }
        return ans;
    }

}
