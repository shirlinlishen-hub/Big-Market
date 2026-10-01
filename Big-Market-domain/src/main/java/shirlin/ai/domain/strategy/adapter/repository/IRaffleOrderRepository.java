package shirlin.ai.domain.strategy.adapter.repository;

import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.strategy.model.entity.DrawOrderEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;

public interface IRaffleOrderRepository {
    DrawOrderEntity findByRequest(String userId, String requestNo);
    DrawOrderEntity reserveDraw(ActivityFactorEntity factor);
    RaffleResultEntity completeDraw(DrawOrderEntity order, RaffleResultEntity candidate);
    void cancelFailedDraw(DrawOrderEntity order);
}
