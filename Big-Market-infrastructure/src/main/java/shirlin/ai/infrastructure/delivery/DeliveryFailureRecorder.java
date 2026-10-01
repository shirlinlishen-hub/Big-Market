package shirlin.ai.infrastructure.delivery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.infrastructure.dao.IAwardDeliveryTaskDao;

@Service
public class DeliveryFailureRecorder {
    private static final int MAX_REASON_LENGTH = 500;

    private final IAwardDeliveryTaskDao taskDao;

    public DeliveryFailureRecorder(IAwardDeliveryTaskDao taskDao) {
        this.taskDao = taskDao;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordFailure(String orderId, String reason) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        String summary = reason == null || reason.isBlank()
                ? "Unknown delivery failure" : reason;
        summary = summary.substring(0, Math.min(summary.length(), MAX_REASON_LENGTH));
        taskDao.recordConsumerFailure(orderId, summary);
    }
}
