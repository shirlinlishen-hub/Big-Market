package shirlin.ai.domain.strategy.model.valobj;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RuleTypeVO {
    RULEWEIGHT(1, "rule_weight",     "ruleWeightFilter"),
    RULELOCK(2,  "rule_lock",        "ruleLockFilter"),
    RULELUCK(3,  "rule_luck",        "ruleLuckFilter"),
    RULEBLACKLIST(4, "rule_blacklist", "ruleBlacklistFilter")
    ;

    private int code;
    private String ruleModel;
    private String ruleBeanName;

    public static String getRuleBeanName(int code){
        for (RuleTypeVO ruleTypeVO : RuleTypeVO.values()) {
            if (ruleTypeVO.getCode()==code){
                return ruleTypeVO.getRuleBeanName();
            }
        }
        return "";
    }
}
