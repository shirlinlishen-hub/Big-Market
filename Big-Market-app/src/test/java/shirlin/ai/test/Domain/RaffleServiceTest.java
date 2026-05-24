package shirlin.ai.test.Domain;


import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import lombok.Locked;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
import shirlin.ai.domain.strategy.service.IRaffleService;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RaffleServiceTest {


    @Resource
    private IRaffleService  raffleService;

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
