package shirlin.ai.job;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import shirlin.ai.infrastructure.adapter.repository.DrawRecoveryService;
import shirlin.ai.infrastructure.dao.IDrawOrderDao;
import shirlin.ai.infrastructure.dao.po.DrawOrder;

import java.util.Date;

@Component
public class DrawRecoveryJob {
    private static final Logger log = LoggerFactory.getLogger(DrawRecoveryJob.class);
    @Resource private IDrawOrderDao orderDao;
    @Resource private DrawRecoveryService service;

    @Scheduled(fixedDelayString = "${big-market.jobs.draw-recovery-delay-ms:60000}")
    public void run() {
        Date before = new Date(System.currentTimeMillis() - 10 * 60_000L);
        for (DrawOrder order : orderDao.selectTimedOut(before)) {
            try {
                service.cancelTimedOut(order.getUserId(), order.getOrderId());
            } catch (Exception e) {
                log.error("Draw recovery failed for order {}", order.getOrderId(), e);
            }
        }
    }
}
