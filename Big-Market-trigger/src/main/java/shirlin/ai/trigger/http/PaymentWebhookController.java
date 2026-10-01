package shirlin.ai.trigger.http;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import shirlin.ai.api.dto.PaymentEventRequestDTO;
import shirlin.ai.api.dto.PaymentEventResponseDTO;
import shirlin.ai.api.response.Response;
import shirlin.ai.domain.Activity.model.entity.PaymentEventCommand;
import shirlin.ai.domain.Activity.model.entity.PaymentEventResult;
import shirlin.ai.domain.Activity.service.IPaymentEventService;
import shirlin.ai.trigger.security.PaymentSignatureVerifier;

@RestController
public class PaymentWebhookController {
    @Resource private PaymentSignatureVerifier signatureVerifier;
    @Resource private IPaymentEventService paymentEventService;

    @PostMapping(value = "/api/v1/internal/payment/events", consumes = "application/json")
    public ResponseEntity<Response<PaymentEventResponseDTO>> accept(
            @RequestHeader("X-Payment-Timestamp") String timestamp,
            @RequestHeader("X-Payment-Signature") String signature,
            @RequestBody String rawBody) {
        signatureVerifier.verify(timestamp, signature, rawBody);
        final PaymentEventRequestDTO request;
        try {
            request = JSON.parseObject(rawBody, PaymentEventRequestDTO.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Payment event body is invalid JSON", e);
        }
        if (request == null) throw new IllegalArgumentException("Payment event body is required");
        PaymentEventResult result = paymentEventService.process(PaymentEventCommand.builder()
                .eventId(request.getEventId()).eventType(request.getEventType())
                .paymentOrderNo(request.getPaymentOrderNo()).userId(request.getUserId())
                .activityId(request.getActivityId()).skuId(request.getSkuId())
                .payloadHash(DigestUtils.sha256Hex(rawBody)).build());
        PaymentEventResponseDTO data = PaymentEventResponseDTO.builder()
                .eventId(result.getEventId()).status(result.getStatus())
                .purchaseOrderId(result.getPurchaseOrderId())
                .removedUnusedCount(result.getRemovedUnusedCount())
                .consumedExposureCount(result.getConsumedExposureCount()).build();
        return ResponseEntity.ok(Response.<PaymentEventResponseDTO>builder()
                .code("0000").info("成功").data(data).build());
    }
}
