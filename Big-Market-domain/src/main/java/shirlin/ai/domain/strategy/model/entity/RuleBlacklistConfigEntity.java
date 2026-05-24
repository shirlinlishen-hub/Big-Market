package shirlin.ai.domain.strategy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 黑名单规则配置
 * ruleValue JSON: {"awardId":9999,"userBlacklist":"user1,user2,user3"}
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuleBlacklistConfigEntity {

    private Integer awardId;
    private String userBlacklist;

}
