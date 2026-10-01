package shirlin.ai.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import shirlin.ai.api.dto.RaffleRequestDTO;
import shirlin.ai.api.dto.RaffleResponseDTO;
import shirlin.ai.api.response.Response;

public interface IRaffleService {
    @PostMapping("/api/v1/raffles/draw")
    ResponseEntity<Response<RaffleResponseDTO>> draw(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody RaffleRequestDTO request);
}
