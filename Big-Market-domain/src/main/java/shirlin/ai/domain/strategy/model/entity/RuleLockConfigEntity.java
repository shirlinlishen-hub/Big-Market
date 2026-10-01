package shirlin.ai.domain.strategy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import com.alibaba.fastjson2.annotation.JSONField;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuleLockConfigEntity {

    @JSONField(name = "unlock_count")
    public int unlockCount;
    @JSONField(name = "locked_award_ids")
    public List<Integer> lockedAwardIds;
}
