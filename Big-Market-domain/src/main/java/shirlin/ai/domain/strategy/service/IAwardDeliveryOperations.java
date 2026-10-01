package shirlin.ai.domain.strategy.service;

import shirlin.ai.domain.strategy.model.entity.AwardDeliveryTaskEntity;

import java.util.List;

public interface IAwardDeliveryOperations {
    AwardDeliveryTaskEntity queryUserTask(String userId, String orderId);
    List<AwardDeliveryTaskEntity> queryByStatus(int status, int limit);
    void retry(String orderId, String operatorId, String note);
    void completeManual(String orderId, String operatorId, boolean success, String note);
}
