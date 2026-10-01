package shirlin.ai.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.infrastructure.dao.IActivityAccountDao;
import shirlin.ai.infrastructure.dao.IDrawOrderDao;
import shirlin.ai.infrastructure.dao.IUsagePeriodDao;
import shirlin.ai.infrastructure.dao.po.DrawOrder;

@Service
public class DrawRecoveryService {
    @Resource private IDrawOrderDao orderDao;
    @Resource private IActivityAccountDao accountDao;
    @Resource private IUsagePeriodDao periodDao;

    @Transactional(rollbackFor = Exception.class)
    public void cancelTimedOut(String userId, String orderId) {
        DrawOrder order = orderDao.selectForUpdate(userId, orderId);
        if (order == null || order.getStatus() != 0) return;
        if (orderDao.cancel(userId, orderId) != 1) return;
        if (accountDao.restoreTotalSurplus(userId, order.getActivityId()) != 1) {
            throw new IllegalStateException("Draw account missing");
        }
        if (periodDao.decrementIfPositive(userId, order.getActivityId(), "M", order.getDrawMonth()) != 1
                || periodDao.decrementIfPositive(userId, order.getActivityId(), "D",
                order.getDrawDay().toString()) != 1) {
            throw new IllegalStateException("Draw period usage missing");
        }
    }
}
