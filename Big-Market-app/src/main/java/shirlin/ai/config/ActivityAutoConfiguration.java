package shirlin.ai.config;


import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import shirlin.ai.domain.Activity.service.Armory.ActivityAmory;
import shirlin.ai.domain.strategy.service.Armory.StrategyArmory;

import java.util.List;
@Slf4j
@Configuration
@EnableConfigurationProperties(ActivityAutoConfigProperties.class)
@ConditionalOnProperty(prefix = "activity.auto-config", name = "enabled", havingValue = "true")
public class ActivityAutoConfiguration implements ApplicationListener<ApplicationReadyEvent> {

    @Resource
    private ActivityAutoConfigProperties activityAutoConfigProperties;
    @Resource
    private ActivityAmory activityAmory;
    @Resource
    private StrategyArmory strategyArmory;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        List<Long> activityIds = activityAutoConfigProperties.getActivityIds();
        List<Long> strategyIds = activityAutoConfigProperties.getStrategyIds();

        if (activityIds == null || activityIds.isEmpty()
                || strategyIds == null || strategyIds.isEmpty()) {
            log.warn("活动或策略配置ID为空, 跳过自动装配");
            return;
        }

        log.info("开始自动装配, 活动ID:{}, 策略ID:{}", activityIds, strategyIds);

        for (Long activityId : activityIds) {
            activityAmory.assembleLotteryActivity(activityId);
        }
        log.info("活动装配完成");

        for (Long strategyId : strategyIds) {
            strategyArmory.assembleLotteryStrategy(strategyId);
        }
        log.info("策略装配完成");
    }
}