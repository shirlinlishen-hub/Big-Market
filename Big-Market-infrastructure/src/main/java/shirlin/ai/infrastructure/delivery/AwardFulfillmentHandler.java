package shirlin.ai.infrastructure.delivery;

import shirlin.ai.domain.strategy.model.valobj.FulfillmentResult;
import shirlin.ai.infrastructure.dao.po.AwardDeliveryTask;

public interface AwardFulfillmentHandler {
    boolean supports(String awardKey);

    FulfillmentResult fulfill(AwardDeliveryTask task);
}
