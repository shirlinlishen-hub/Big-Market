package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import shirlin.ai.domain.strategy.model.entity.RuleWeightConfigEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeightGroupSelectionTest {
    @Test
    void highestSatisfiedThresholdWinsRegardlessOfJsonOrder() {
        RuleWeightConfigEntity.WeightGroup low = new RuleWeightConfigEntity.WeightGroup();
        low.setGroupId("low"); low.setThresholdValue(10);
        RuleWeightConfigEntity.WeightGroup high = new RuleWeightConfigEntity.WeightGroup();
        high.setGroupId("high"); high.setThresholdValue(50);
        RuleWeightConfigEntity config = RuleWeightConfigEntity.builder()
                .thresholdKey("draw_count").groups(List.of(low, high)).build();
        assertEquals("high", config.getMatchedGroup(55).getGroupId());
    }
}
