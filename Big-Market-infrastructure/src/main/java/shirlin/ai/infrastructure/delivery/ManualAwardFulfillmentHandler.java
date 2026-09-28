package shirlin.ai.infrastructure.delivery;

import org.springframework.stereotype.Component;
import shirlin.ai.domain.strategy.model.valobj.FulfillmentResult;
import shirlin.ai.infrastructure.dao.po.AwardDeliveryTask;

import java.util.Set;

@Component
public class ManualAwardFulfillmentHandler implements AwardFulfillmentHandler {
    private static final Set<String> SUPPORTED_KEYS = Set.of(
            "coupon_center", "physical_goods");

    @Override
    public boolean supports(String awardKey) {
        return SUPPORTED_KEYS.contains(awardKey);
    }

    @Override
    public FulfillmentResult fulfill(AwardDeliveryTask task) {
        return FulfillmentResult.MANUAL;
    }
}
