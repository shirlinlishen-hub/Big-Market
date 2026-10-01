package shirlin.ai.domain.strategy.model.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.alibaba.fastjson2.annotation.JSONField;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuleLuckConfigEntity {

    /** 对应 DB JSON luck_threshold */
    @JSONField(name = "luck_threshold")
    private int luckThreshold;

    /** 对应 DB JSON luck_award_id */
    @JSONField(name = "luck_award_id")
    private int luckAwardId;

    /** 对应 DB JSON luck_increment */
    @JSONField(name = "luck_increment")
    private int luckIncrement;
}
