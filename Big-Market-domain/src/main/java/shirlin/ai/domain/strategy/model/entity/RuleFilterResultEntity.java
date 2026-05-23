package shirlin.ai.domain.strategy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 规则过滤结果
 */
public class RuleFilterResultEntity {

    private Type type;
    private Integer awardId;           // TAKE_OVER时有值
    private Set<Integer> excludeAwardIds; // ALLOW时可能有排除项

    public enum Type {
        ALLOW,      // 放行, 继续后续流程，
        TAKE_OVER   // 接管, 直接返回指定奖品，兜底商品
    }

}
