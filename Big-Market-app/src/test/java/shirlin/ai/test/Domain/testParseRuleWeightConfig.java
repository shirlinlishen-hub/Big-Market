package shirlin.ai.test.Domain;


import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import shirlin.ai.domain.strategy.model.entity.RuleWeightConfigEntity;


@Slf4j
public class testParseRuleWeightConfig {

    @Test
    void test(){
        String json = """
        {
            "threshold_key": "total_spend",
            "groups": [
                {
                    "threshold_value": 5000,
                    "group_id": "weight_group_5000",
                    "desc": "顶级用户",
                    "award_rates": {"101": 0.01, "102": 0.10}
                },
                {
                    "threshold_value": 1000,
                    "group_id": "weight_group_1000",
                    "desc": "高消费用户",
                    "award_rates": {"101": 0.001, "102": 0.05}
                }
            ]
        }
        """;

        RuleWeightConfigEntity config = JSON.parseObject(
                json, RuleWeightConfigEntity.class
        );

        // 验证基本解析
        assert config.getThresholdKey() != null : "thresholdKey 解析为null, 下划线映射失败";
        assert config.getGroups().size() == 2 : "groups 数量不对";
        assert config.getGroups().get(0).getThresholdValue() == 5000 : "thresholdValue 解析失败";
        assert config.getGroups().get(0).getAwardRates().get(101) != null : "awardRates key转换失败";

        // 验证匹配逻辑
        assert config.getMatchedGroup(8000).getGroupId().equals("weight_group_5000");
        assert config.getMatchedGroup(3000).getGroupId().equals("weight_group_1000");
        assert config.getMatchedGroup(500) == null;

        System.out.println("解析和匹配全部通过");
    }
}