package shirlin.ai.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.infrastructure.dao.IActivityAccountDao;
import shirlin.ai.infrastructure.dao.IActivityDao;
import shirlin.ai.infrastructure.dao.IUserAwardRecordDao;
import shirlin.ai.infrastructure.dao.po.Activity;
import shirlin.ai.infrastructure.dao.po.ActivityAccount;
import shirlin.ai.infrastructure.dao.po.UserAwardRecord;
import shirlin.ai.infrastructure.redis.IRedisService;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Repository
public class ActivityRepository implements IActivityRepository {

    private static final String SKU_STOCK_KEY       = "big_market:activity:sku:stock:";
    private static final String SKU_STOCK_LOCK_KEY  = "big_market:activity:sku:stock:lock:";
    private static final String SKU_STOCK_QUEUE_KEY = "big_market:activity:sku:stock:queue";
    private static final String MONTHLY_QUOTA_KEY   = "big_market:user:quota:monthly:";
    private static final String DAILY_QUOTA_KEY     = "big_market:user:quota:daily:";

    @Resource
    private IActivityDao activityDao;

    @Resource
    private IActivityAccountDao activityAccountDao;

    @Resource
    private IUserAwardRecordDao userAwardRecordDao;

    @Resource
    private IRedisService redisService;

    private Long queryActivityId(Long strategyId) {
        Activity activity = activityDao.selectByStrategyId(strategyId);
        if (activity == null) {
            // 理想错误码应为 ACTIVITY_NOT_EXISTS（活动记录不存在），但枚举中暂未定义该值。
            // ResponseCode 中最接近的语义是 STRATEGY_NOT_ACTIVE（0003，"活动未开始或已下线"），
            // 当前以其代替；如后续新增 ACTIVITY_NOT_EXISTS 枚举值，请同步替换此处。
            throw new AppException(ResponseCode.STRATEGY_NOT_ACTIVE.getInfo());
        }
        return activity.getActivityId();
    }

    @Override
    public boolean deductActivitySkuStock(Long strategyId) {
        String stockKey = SKU_STOCK_KEY + strategyId;

        if (!redisService.isExists(stockKey)) {
            return true;
        }

        long remaining = redisService.decr(stockKey);
        if (remaining < 0) {
            redisService.setValue(stockKey, 0L);
            return false;
        }

        String lockKey = SKU_STOCK_LOCK_KEY + strategyId + ":" + remaining;
        boolean locked = redisService.setNx(lockKey);
        if (!locked) {
            redisService.incrBy(stockKey, 1L);
            return false;
        }

        org.redisson.api.RBlockingQueue<Long> blockingQueue =
                redisService.getBlockingQueue(SKU_STOCK_QUEUE_KEY);
        org.redisson.api.RDelayedQueue<Long> delayedQueue =
                redisService.getDelayedQueue(blockingQueue);
        delayedQueue.offer(strategyId, 3, TimeUnit.SECONDS);

        return true;
    }

    @Override
    public void cacheActivitySkuStock(Long strategyId, Long totalStock) {
        redisService.setAtomicLong(SKU_STOCK_KEY + strategyId, totalStock);
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
    public boolean deductUserTotalQuota(String userId, Long strategyId) {
        Long activityId = queryActivityId(strategyId);
        int affected = activityAccountDao.deductTotalSurplus(userId, activityId);
        return affected > 0;
    }

    @Override
    public boolean deductUserMonthlyQuota(String userId, Long strategyId) {
        Long activityId = queryActivityId(strategyId);

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
    public boolean deductUserDailyQuota(String userId, Long strategyId) {
        Long activityId = queryActivityId(strategyId);

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
}
