package shirlin.ai.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RaffleResponseDTO {
    private Integer awardId;
    private String awardTitle;
    private Integer awardType;
}
