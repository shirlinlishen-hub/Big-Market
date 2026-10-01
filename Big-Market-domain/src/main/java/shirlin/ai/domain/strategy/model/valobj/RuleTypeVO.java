package shirlin.ai.domain.strategy.model.valobj;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RuleTypeVO {
    RULEWEIGHT(1, "权重规则，根据用户属性配置不同的概率", "rule_weight"),
    RULELOCK(2,   "N次解锁",                           "rule_lock"),
    RULELUCK(3,   "运气值兜底",                         "rule_luck"),
    RULEBLACKLIST(4, "黑名单限制，黑名单用户直接兜底",    "rule_blacklist"),
    RULEFALLBACK(5, "显式保底奖", "rule_fallback")
    ;

    private int code;
    private String info;
    private String ruleModel;
}
