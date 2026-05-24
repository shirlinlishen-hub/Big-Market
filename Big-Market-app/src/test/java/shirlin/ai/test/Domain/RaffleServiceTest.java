package shirlin.ai.test.Domain;


import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.service.IActivityArmory;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
import shirlin.ai.domain.strategy.service.IRaffleService;
import shirlin.ai.domain.strategy.service.IStrategyArmory;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RaffleServiceTest {

    @Resource
    private IRaffleService raffleService;

    @Resource
    private IActivityArmory activityArmory;

    @Resource
    private IStrategyArmory strategyArmory;

    @Before
    public void setUp() {
        strategyArmory.assembleLotteryStrategy(100001L);
        activityArmory.assembleLotteryActivity(20001L);
    }

    @Test
    public void test() {
        ActivityFactorEntity activityFactorEntity = new ActivityFactorEntity();
        activityFactorEntity.setUserId("user_001");
        activityFactorEntity.setActivityId(20001L);
        activityFactorEntity.setStrategyId(100001L);
        activityFactorEntity.setSkuId(30001L);
        RaffleResultEntity ans = raffleService.doRaffle(activityFactorEntity);
        log.info("抽奖测试结果：{}", JSONObject.toJSONString(ans));

    }
}
