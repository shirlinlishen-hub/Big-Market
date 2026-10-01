package shirlin.ai.domain.Activity.service.Rule.Chain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.Activity.service.IActivityChainHandler;
import shirlin.ai.domain.Activity.service.Rule.Chain.Impl.ActivityInfoCheckHandler;

/**
 * 活动校验责任链工厂
 * 当前仅保留活动配置校验；库存统一在MySQL购买事务中预占。
 */
@Service
public class ActivityChainHandlerFactory {

    @Resource
    private ActivityInfoCheckHandler activityInfoCheckHandler;

    /** 链头（不可变，应用启动后组装一次） */
    private IActivityChainHandler chainHead;

    @PostConstruct
    public void init() {
        chainHead = activityInfoCheckHandler;
    }

    public IActivityChainHandler getChainHead() {
        return chainHead;
    }

}
