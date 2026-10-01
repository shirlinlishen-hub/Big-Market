package shirlin.ai.domain.strategy.service;

import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.strategy.model.entity.DrawOrderEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;

public interface IRaffleOrderService {

    DrawOrderEntity reserveDraw(ActivityFactorEntity factor);
    RaffleResultEntity completeDraw(DrawOrderEntity order, RaffleResultEntity candidate);
    void cancelFailedDraw(DrawOrderEntity order);
}
