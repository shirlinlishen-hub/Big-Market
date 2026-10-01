package shirlin.ai.domain.strategy.service.Rule;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;

import java.util.List;

/** Requires an explicit, unlimited-stock award for all fallback paths. */
public final class FallbackAwardPolicy {
    private FallbackAwardPolicy() { }

    public static Integer requireValid(String ruleValue, List<StrategyAwardEntity> awards) {
        if (ruleValue == null || ruleValue.isBlank() || awards == null) {
            throw new IllegalArgumentException("Fallback award configuration is missing");
        }
        JSONObject config;
        try {
            config = JSON.parseObject(ruleValue);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Fallback award configuration is invalid", e);
        }
        Object rawId = config == null ? null : config.get("award_id");
        Integer awardId;
        try {
            awardId = rawId instanceof Number
                    ? new java.math.BigDecimal(rawId.toString()).intValueExact() : null;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Fallback award ID must be an integer", e);
        }
        if (awardId == null || awardId <= 0) {
            throw new IllegalArgumentException("Fallback award ID is missing");
        }
        boolean valid = awards.stream().anyMatch(award -> awardId.equals(award.getAwardId())
                && Integer.valueOf(-1).equals(award.getAwardCount())
                && Integer.valueOf(-1).equals(award.getAwardSurplus()));
        if (!valid) throw new IllegalArgumentException("Fallback award must have unlimited stock");
        return awardId;
    }
}
