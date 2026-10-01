package shirlin.ai.job;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import shirlin.ai.infrastructure.adapter.repository.AwardDeliveryService;
import shirlin.ai.infrastructure.dao.IAwardDeliveryTaskDao;
import shirlin.ai.infrastructure.dao.po.AwardDeliveryTask;

@Component
@ConditionalOnExpression("'${big-market.delivery.mode:local}' == 'local' || "
        + "'${big-market.delivery.mode:local}' == 'mq-prepare'")
public class AwardDeliveryJob {
    private static final Logger log = LoggerFactory.getLogger(AwardDeliveryJob.class);
    @Resource private IAwardDeliveryTaskDao taskDao;
    @Resource private AwardDeliveryService service;

    @Scheduled(fixedDelayString = "${big-market.jobs.delivery-delay-ms:5000}")
    public void run() {
        for (AwardDeliveryTask task : taskDao.selectDueTasks()) {
            try {
                service.deliver(task.getUserId(), task.getOrderId());
            } catch (Exception e) {
                log.error("Award delivery failed for order {}", task.getOrderId(), e);
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                taskDao.recordFailure(task.getUserId(), task.getOrderId(),
                        reason.substring(0, Math.min(reason.length(), 500)));
            }
        }
    }
}
