package shirlin.ai.domain.strategy.model.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 权重规则配置
 * ruleValue JSON: 嵌套JSON,外层配置 + 内层分组列表
 * key: 累计抽奖次数阈值；value: 该权重等级允许参与抽奖的奖品ID列表
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuleWeightConfigEntity {

    @JSONField(name = "threshold_key")
    private String thresholdKey;

    private List<WeightGroup> groups;  // 字段名一致，不用加注解

    @Data
    public static class WeightGroup {

        @JSONField(name = "threshold_value")
        private Integer thresholdValue;

        @JSONField(name = "group_id")
        private String groupId;

        private String desc;

        @JSONField(name = "award_rates")
        private Map<Integer, BigDecimal> awardRates;
    }

    public WeightGroup getMatchedGroup(int userThresholdValue) {
        if (groups == null) return null;
        return groups.stream()
                .filter(group -> group != null && group.getThresholdValue() != null
                        && group.getThresholdValue() <= userThresholdValue)
                .max(java.util.Comparator.comparing(WeightGroup::getThresholdValue))
                .orElse(null);
    }

    public Map<Integer, BigDecimal> getAwardRates(int userThresholdValue) {
        WeightGroup group = getMatchedGroup(userThresholdValue);
        return group != null ? group.getAwardRates() : null;
    }
}
