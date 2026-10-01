package shirlin.ai.trigger.http;

import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import shirlin.ai.api.dto.AwardDeliveryResponseDTO;
import shirlin.ai.api.dto.DeliveryRetryRequestDTO;
import shirlin.ai.api.dto.ManualDeliveryRequestDTO;
import shirlin.ai.api.response.Response;
import shirlin.ai.domain.strategy.model.entity.AwardDeliveryTaskEntity;
import shirlin.ai.domain.strategy.service.IAwardDeliveryOperations;
import shirlin.ai.trigger.security.JwtIdentity;
import shirlin.ai.trigger.security.JwtIdentityVerifier;

import java.util.List;

@RestController
public class AwardDeliveryController {
    @Resource private IAwardDeliveryOperations operations;
    @Resource private JwtIdentityVerifier identityVerifier;

    @GetMapping("/api/v1/awards/{orderId}/delivery")
    public ResponseEntity<Response<AwardDeliveryResponseDTO>> queryOwn(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String orderId) {
        JwtIdentity identity = identityVerifier.verify(authorization);
        return ResponseEntity.ok(success(toDto(operations.queryUserTask(identity.userId(), orderId))));
    }

    @GetMapping("/api/v1/admin/award-deliveries")
    public ResponseEntity<Response<List<AwardDeliveryResponseDTO>>> list(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "3") int status,
            @RequestParam(defaultValue = "100") int limit) {
        requireOperator(authorization);
        List<AwardDeliveryResponseDTO> data = operations.queryByStatus(status, limit)
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(success(data));
    }

    @PostMapping("/api/v1/admin/award-deliveries/{orderId}/retry")
    public ResponseEntity<Response<Void>> retry(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String orderId,
            @RequestBody DeliveryRetryRequestDTO request) {
        JwtIdentity operator = requireOperator(authorization);
        operations.retry(orderId, operator.userId(), request == null ? null : request.getNote());
        return ResponseEntity.ok(success(null));
    }

    @PostMapping("/api/v1/admin/award-deliveries/{orderId}/complete")
    public ResponseEntity<Response<Void>> complete(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String orderId,
            @RequestBody ManualDeliveryRequestDTO request) {
        JwtIdentity operator = requireOperator(authorization);
        if (request == null || request.getSuccess() == null) {
            throw new IllegalArgumentException("Manual result is required");
        }
        operations.completeManual(orderId, operator.userId(), request.getSuccess(), request.getNote());
        return ResponseEntity.ok(success(null));
    }

    private JwtIdentity requireOperator(String authorization) {
        JwtIdentity identity = identityVerifier.verify(authorization);
        if (!identity.hasRole("operations")) throw new SecurityException("Operations role is required");
        return identity;
    }

    private AwardDeliveryResponseDTO toDto(AwardDeliveryTaskEntity task) {
        return AwardDeliveryResponseDTO.builder().orderId(task.getOrderId()).userId(task.getUserId())
                .awardId(task.getAwardId()).awardType(task.getAwardType()).awardKey(task.getAwardKey())
                .awardValue(task.getAwardValue()).status(task.getStatus()).attempts(task.getAttempts())
                .nextRetryAt(task.getNextRetryAt()).lastError(task.getLastError()).build();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder().code("0000").info("成功").data(data).build();
    }
}
