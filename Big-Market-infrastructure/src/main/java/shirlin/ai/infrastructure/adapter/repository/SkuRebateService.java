package shirlin.ai.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.infrastructure.dao.IActivityAccountDao;
import shirlin.ai.infrastructure.dao.ISkuRebateDao;
import shirlin.ai.infrastructure.dao.po.SkuRebateOrder;

@Service
public class SkuRebateService {
    @Resource private ISkuRebateDao rebateDao;
    @Resource private IActivityAccountDao accountDao;

    @Transactional(rollbackFor = Exception.class)
    public void grant(String purchaseOrderId) {
        SkuRebateOrder order = rebateDao.selectForUpdate(purchaseOrderId);
        if (order == null || order.getStatus() != 0) return;
        if (order.getRebateDrawCount() == null || order.getRebateDrawCount() <= 0) {
            throw new IllegalArgumentException("Invalid rebate configuration");
        }
        if (accountDao.grantTotalOnly(order.getUserId(), order.getActivityId(),
                order.getRebateDrawCount()) != 1) {
            throw new IllegalStateException("Draw account missing");
        }
        if (rebateDao.complete(purchaseOrderId) != 1) {
            throw new IllegalStateException("Rebate state changed");
        }
    }
}
