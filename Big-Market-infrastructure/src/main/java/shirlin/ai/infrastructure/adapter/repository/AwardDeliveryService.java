package shirlin.ai.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.infrastructure.dao.IAwardDeliveryTaskDao;
import shirlin.ai.infrastructure.dao.IAwardDeliveryAuditDao;
import shirlin.ai.infrastructure.dao.IUserAwardRecordDao;
import shirlin.ai.infrastructure.dao.po.AwardDeliveryTask;
import shirlin.ai.domain.strategy.model.entity.AwardDeliveryTaskEntity;
import shirlin.ai.domain.strategy.service.IAwardDeliveryOperations;
import shirlin.ai.infrastructure.delivery.AwardFulfillmentService;

import java.util.List;
import java.util.Map;

@Service
public class AwardDeliveryService implements IAwardDeliveryOperations {
    @Resource private IAwardDeliveryTaskDao taskDao;
    @Resource private IUserAwardRecordDao awardRecordDao;
    @Resource private IAwardDeliveryAuditDao auditDao;
    @Resource private AwardFulfillmentService fulfillmentService;
    @Resource private TransactionalOutboxService outboxService;

    @Transactional(rollbackFor = Exception.class)
    public void deliver(String userId, String orderId) {
        fulfillmentService.fulfill(userId, orderId);
    }

    @Override
    public AwardDeliveryTaskEntity queryUserTask(String userId, String orderId) {
        AwardDeliveryTask task = taskDao.selectByUserAndOrder(userId, orderId);
        if (task == null) throw new IllegalArgumentException("Delivery task was not found");
        return toEntity(task);
    }

    @Override
    public List<AwardDeliveryTaskEntity> queryByStatus(int status, int limit) {
        if (status < 0 || status > 4) throw new IllegalArgumentException("Invalid delivery status");
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return taskDao.selectByStatus(status, boundedLimit).stream().map(this::toEntity).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retry(String orderId, String operatorId, String note) {
        AwardDeliveryTask task = taskDao.selectByOrderIdForUpdate(orderId);
        requireOperatorAction(task, operatorId, note);
        int nextVersion = Math.addExact(task.getDispatchVersion(), 1);
        if (taskDao.requeue(orderId) != 1
                || auditDao.insert(orderId, operatorId, "RETRY", note) != 1) {
            throw new IllegalStateException("Delivery task cannot be retried from its current state");
        }
        outboxService.append(
                "AWARD_DELIVERY_REQUESTED",
                "award-delivery:" + orderId + ":v" + nextVersion,
                orderId,
                task.getUserId(),
                Map.of("orderId", orderId, "userId", task.getUserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeManual(String orderId, String operatorId, boolean success, String note) {
        AwardDeliveryTask task = taskDao.selectByOrderIdForUpdate(orderId);
        requireOperatorAction(task, operatorId, note);
        int taskUpdated = success ? taskDao.markManualSuccess(orderId)
                : taskDao.markManualFailed(orderId, note);
        int awardState = success ? 2 : 3;
        if (taskUpdated != 1
                || awardRecordDao.updateAwardStateByOrder(task.getUserId(), orderId, awardState) != 1
                || auditDao.insert(orderId, operatorId,
                success ? "MANUAL_SUCCESS" : "MANUAL_FAILED", note) != 1) {
            throw new IllegalStateException("Manual delivery state changed");
        }
    }

    private void requireOperatorAction(AwardDeliveryTask task, String operatorId, String note) {
        if (task == null || (task.getStatus() != 3 && task.getStatus() != 4)) {
            throw new IllegalStateException("Only manual or failed tasks can be operated");
        }
        if (operatorId == null || operatorId.isBlank() || note == null || note.isBlank()
                || note.length() > 500) {
            throw new IllegalArgumentException("Operator and a 1-500 character note are required");
        }
    }

    private AwardDeliveryTaskEntity toEntity(AwardDeliveryTask task) {
        return AwardDeliveryTaskEntity.builder().orderId(task.getOrderId()).userId(task.getUserId())
                .awardId(task.getAwardId()).awardType(task.getAwardType()).awardKey(task.getAwardKey())
                .awardValue(task.getAwardValue()).status(task.getStatus()).attempts(task.getAttempts())
                .nextRetryAt(task.getNextRetryAt()).lastError(task.getLastError())
                .dispatchVersion(task.getDispatchVersion()).build();
    }
}
