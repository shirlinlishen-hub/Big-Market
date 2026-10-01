package shirlin.ai.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.*;
import shirlin.ai.domain.Activity.service.IActivitySkuService;
import shirlin.ai.domain.Activity.service.IPaymentEventService;
import shirlin.ai.infrastructure.dao.IPaymentEventDao;
import shirlin.ai.infrastructure.dao.po.PaymentEvent;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

import java.util.Set;

@Service
public class PaymentEventService implements IPaymentEventService {
    private static final Set<String> SUPPORTED_TYPES = Set.of("PAYMENT_SUCCEEDED", "REFUNDED", "CANCELLED");

    @Resource private IPaymentEventDao eventDao;
    @Resource private IActivitySkuService skuService;
    @Resource private IActivityRepository activityRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentEventResult process(PaymentEventCommand command) {
        validate(command);
        PaymentEvent existing = eventDao.selectByEventId(command.getEventId());
        if (existing != null) return replay(existing, command.getPayloadHash());

        PaymentEvent event = toPo(command);
        if (eventDao.insertIgnore(event) != 1) {
            existing = eventDao.selectByEventId(command.getEventId());
            if (existing == null) throw new IllegalStateException("Payment event insert race was not resolved");
            return replay(existing, command.getPayloadHash());
        }

        String purchaseOrderId;
        int removed = 0;
        int exposure = 0;
        int eventStatus = 1;
        if ("PAYMENT_SUCCEEDED".equals(command.getEventType())) {
            ActivityOrderEntity order = skuService.createOrder(ActivityFactorEntity.builder()
                    .userId(command.getUserId()).activityId(command.getActivityId())
                    .skuId(command.getSkuId()).outBusinessNo(command.getPaymentOrderNo()).build());
            purchaseOrderId = order.getOrderId();
        } else {
            QualificationRevokeResult result = activityRepository.revokePurchase(
                    command.getUserId(), command.getActivityId(), command.getSkuId(),
                    command.getPaymentOrderNo(), command.getEventId());
            purchaseOrderId = result.getPurchaseOrderId();
            removed = result.getRemovedUnusedCount();
            exposure = result.getConsumedExposureCount();
            eventStatus = result.isManualReview() ? 2 : 1;
        }
        if (eventDao.markResult(command.getEventId(), eventStatus, purchaseOrderId, removed, exposure) != 1) {
            throw new IllegalStateException("Payment event state changed");
        }
        return result(event, eventStatus, purchaseOrderId, removed, exposure);
    }

    private void validate(PaymentEventCommand c) {
        if (c == null || blank(c.getEventId()) || blank(c.getEventType()) || blank(c.getPaymentOrderNo())
                || blank(c.getUserId()) || c.getActivityId() == null || c.getSkuId() == null
                || blank(c.getPayloadHash()) || !SUPPORTED_TYPES.contains(c.getEventType())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode());
        }
    }

    private PaymentEventResult replay(PaymentEvent existing, String payloadHash) {
        if (!existing.getPayloadHash().equals(payloadHash)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "Payment event ID was reused with different content");
        }
        if (existing.getStatus() == 0) {
            throw new IllegalStateException("Payment event is being processed");
        }
        return result(existing, existing.getStatus(), existing.getPurchaseOrderId(),
                existing.getRemovedUnusedCount(), existing.getConsumedExposureCount());
    }

    private PaymentEvent toPo(PaymentEventCommand c) {
        PaymentEvent event = new PaymentEvent();
        event.setEventId(c.getEventId()); event.setEventType(c.getEventType());
        event.setPaymentOrderNo(c.getPaymentOrderNo()); event.setUserId(c.getUserId());
        event.setActivityId(c.getActivityId()); event.setSkuId(c.getSkuId());
        event.setPayloadHash(c.getPayloadHash()); event.setStatus(0);
        return event;
    }

    private PaymentEventResult result(PaymentEvent event, int status, String orderId,
                                      Integer removed, Integer exposure) {
        return PaymentEventResult.builder().eventId(event.getEventId())
                .status(status == 2 ? "MANUAL_REVIEW" : "PROCESSED")
                .purchaseOrderId(orderId).removedUnusedCount(removed == null ? 0 : removed)
                .consumedExposureCount(exposure == null ? 0 : exposure).build();
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
