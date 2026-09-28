package shirlin.ai.infrastructure.delivery;

import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.model.valobj.FulfillmentResult;
import shirlin.ai.infrastructure.dao.IAwardDeliveryTaskDao;
import shirlin.ai.infrastructure.dao.IUserAwardRecordDao;
import shirlin.ai.infrastructure.dao.po.AwardDeliveryTask;
import shirlin.ai.infrastructure.messaging.exception.NonRetryableDeliveryException;
import shirlin.ai.infrastructure.messaging.exception.RetryableDeliveryException;

import java.util.List;

@Service
public class AwardFulfillmentService {
    private final IAwardDeliveryTaskDao taskDao;
    private final IUserAwardRecordDao awardRecordDao;
    private final List<AwardFulfillmentHandler> handlers;

    public AwardFulfillmentService(IAwardDeliveryTaskDao taskDao,
                                   IUserAwardRecordDao awardRecordDao,
                                   List<AwardFulfillmentHandler> handlers) {
        this.taskDao = taskDao;
        this.awardRecordDao = awardRecordDao;
        this.handlers = handlers;
    }

    public FulfillmentResult fulfill(String userId, String orderId) {
        AwardDeliveryTask task = taskDao.selectForUpdate(userId, orderId);
        if (task == null) {
            throw new NonRetryableDeliveryException("Award delivery task was not found");
        }
        if (task.getStatus() == 2) {
            return FulfillmentResult.SUCCESS;
        }
        if (task.getStatus() == 3 || task.getStatus() == 4) {
            return FulfillmentResult.MANUAL;
        }
        if (task.getStatus() != 0 || taskDao.claim(orderId) != 1) {
            throw new RetryableDeliveryException("Award delivery task cannot be claimed");
        }

        AwardFulfillmentHandler handler = uniqueHandler(task.getAwardKey());
        FulfillmentResult result = handler.fulfill(task);
        if (result == FulfillmentResult.SUCCESS) {
            completeSuccess(task);
        } else {
            completeManual(task);
        }
        return result;
    }

    private AwardFulfillmentHandler uniqueHandler(String awardKey) {
        List<AwardFulfillmentHandler> matches = handlers.stream()
                .filter(handler -> handler.supports(awardKey))
                .toList();
        if (matches.size() != 1) {
            throw new NonRetryableDeliveryException(
                    "Expected exactly one fulfillment handler for award key " + awardKey);
        }
        return matches.getFirst();
    }

    private void completeSuccess(AwardDeliveryTask task) {
        if (awardRecordDao.updateAwardStateByOrder(
                task.getUserId(), task.getOrderId(), 2) != 1
                || taskDao.markSuccess(task.getOrderId()) != 1) {
            throw new RetryableDeliveryException("Award success state changed concurrently");
        }
    }

    private void completeManual(AwardDeliveryTask task) {
        String reason = "Manual fulfillment required: " + task.getAwardKey();
        if (taskDao.markManual(task.getOrderId(), reason) != 1
                || awardRecordDao.updateAwardStateByOrder(
                task.getUserId(), task.getOrderId(), 3) != 1) {
            throw new RetryableDeliveryException("Award manual state changed concurrently");
        }
    }
}
