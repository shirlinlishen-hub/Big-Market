package shirlin.ai.job;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import shirlin.ai.infrastructure.adapter.repository.SkuRebateService;
import shirlin.ai.infrastructure.dao.ISkuRebateDao;
import shirlin.ai.infrastructure.dao.po.SkuRebateOrder;

@Component
public class SkuRebateJob {
    private static final Logger log = LoggerFactory.getLogger(SkuRebateJob.class);
    @Resource private ISkuRebateDao rebateDao;
    @Resource private SkuRebateService service;

    @Scheduled(fixedDelayString = "${big-market.jobs.rebate-delay-ms:5000}")
    public void run() {
        for (SkuRebateOrder order : rebateDao.selectPending()) {
            try {
                service.grant(order.getPurchaseOrderId());
            } catch (Exception e) {
                log.error("SKU rebate failed for purchase {}", order.getPurchaseOrderId(), e);
            }
        }
    }
}
