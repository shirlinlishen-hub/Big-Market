package shirlin.ai.trigger.http;

import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import shirlin.ai.api.dto.RaffleRequestDTO;
import shirlin.ai.api.dto.RaffleResponseDTO;
import shirlin.ai.api.response.Response;
import shirlin.ai.api.IRaffleService;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
import shirlin.ai.trigger.security.JwtIdentity;
import shirlin.ai.trigger.security.JwtIdentityVerifier;

@Slf4j
@RestController
public class RaffleController implements IRaffleService {
    @Resource private shirlin.ai.domain.strategy.service.IRaffleService raffleService;
    @Resource private JwtIdentityVerifier identityVerifier;

    @Override
    public ResponseEntity<Response<RaffleResponseDTO>> draw(String authorization,
                                                            String idempotencyKey,
                                                            RaffleRequestDTO request) {
        JwtIdentity identity = identityVerifier.verify(authorization);
        if (request == null || request.getActivityId() == null || idempotencyKey == null
                || idempotencyKey.length() < 8 || idempotencyKey.length() > 64) {
            throw new IllegalArgumentException("Activity and an 8-64 character idempotency key are required");
        }
        RaffleResultEntity result = raffleService.doRaffle(ActivityFactorEntity.builder()
                .userId(identity.userId()).activityId(request.getActivityId())
                .outBusinessNo(idempotencyKey).build());
        RaffleResponseDTO data = new RaffleResponseDTO();
        data.setAwardId(result.getAwardId()); data.setAwardTitle(result.getAwardTitle());
        data.setAwardType(result.getAwardType());
        return ResponseEntity.ok(Response.<RaffleResponseDTO>builder()
                .code("0000").info("成功").data(data).build());
    }
}
