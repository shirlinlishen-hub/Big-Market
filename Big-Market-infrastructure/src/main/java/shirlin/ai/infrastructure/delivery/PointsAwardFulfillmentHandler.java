package shirlin.ai.infrastructure.delivery;

import org.springframework.stereotype.Component;
import shirlin.ai.domain.strategy.model.valobj.FulfillmentResult;
import shirlin.ai.infrastructure.dao.IUserPointsDao;
import shirlin.ai.infrastructure.dao.po.AwardDeliveryTask;
import shirlin.ai.infrastructure.dao.po.UserPointsLedger;
import shirlin.ai.infrastructure.messaging.exception.NonRetryableDeliveryException;
import shirlin.ai.infrastructure.messaging.exception.RetryableDeliveryException;

@Component
public class PointsAwardFulfillmentHandler implements AwardFulfillmentHandler {
    private final IUserPointsDao pointsDao;

    public PointsAwardFulfillmentHandler(IUserPointsDao pointsDao) {
        this.pointsDao = pointsDao;
    }

    @Override
    public boolean supports(String awardKey) {
        return "user_points".equals(awardKey) || "random_points".equals(awardKey);
    }

    @Override
    public FulfillmentResult fulfill(AwardDeliveryTask task) {
        int amount = pointsAmount(task);
        UserPointsLedger ledger = pointsDao.selectLedgerForUpdate(task.getOrderId());
        if (ledger == null) {
            if (pointsDao.insertLedger(task.getOrderId(), task.getUserId(), amount) != 1) {
                throw new RetryableDeliveryException("Points ledger insert raced");
            }
            if (pointsDao.addPoints(task.getUserId(), amount) <= 0) {
                throw new RetryableDeliveryException("Points account update failed");
            }
            return FulfillmentResult.SUCCESS;
        }
        return ledger.matches(task.getUserId(), amount)
                ? FulfillmentResult.SUCCESS : FulfillmentResult.MANUAL;
    }

    private int pointsAmount(AwardDeliveryTask task) {
        try {
            int amount = Integer.parseInt(task.getAwardValue());
            if (amount <= 0) {
                throw new NonRetryableDeliveryException("Points award must be positive");
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new NonRetryableDeliveryException("Points award is not an integer", e);
        }
    }
}
