package shirlin.ai.trigger.job;


import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import shirlin.ai.domain.strategy.service.IRaffleStock;

@Slf4j
@Component
public class UpdateAwardStockJob {

    @Resource
    private IRaffleStock raffleStock;

    @Scheduled(cron = "0 */5 * * * *")
    public void updateAwardStock() {
        //异步更新DB的SKU库存
        try {
            log.info("定时任务，更新奖品消耗库存");
        } catch (Exception e) {
            log.error("定时任务，更新奖品消耗库存失败", e);
        }




    }


}
