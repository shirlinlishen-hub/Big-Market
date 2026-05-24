package shirlin.ai.domain.Activity.service.Rule.Chain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.Activity.service.IActivityChainHandler;
import shirlin.ai.domain.Activity.service.Rule.Chain.Impl.ActivityInfoCheckHandler;
import shirlin.ai.domain.Activity.service.Rule.Chain.Impl.ActivitySkuStockHandler;

/**
 * 活动校验责任链工厂
 * 装配顺序：ActivityInfoCheckHandler → ActivitySkuStockHandler
 */
@Service
public class ActivityChainHandlerFactory {

    @Resource
    private ActivityInfoCheckHandler activityInfoCheckHandler;

    @Resource
    private ActivitySkuStockHandler activitySkuStockHandler;

    /** 链头（不可变，应用启动后组装一次） */
    private IActivityChainHandler chainHead;

    @PostConstruct
    public void init() {
        activityInfoCheckHandler.appendNext(activitySkuStockHandler);
        chainHead = activityInfoCheckHandler;
    }

    public IActivityChainHandler getChainHead() {
        return chainHead;
    }

}
