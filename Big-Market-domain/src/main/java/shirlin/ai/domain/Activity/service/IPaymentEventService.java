package shirlin.ai.domain.Activity.service;

import shirlin.ai.domain.Activity.model.entity.PaymentEventCommand;
import shirlin.ai.domain.Activity.model.entity.PaymentEventResult;

public interface IPaymentEventService {
    PaymentEventResult process(PaymentEventCommand command);
}
