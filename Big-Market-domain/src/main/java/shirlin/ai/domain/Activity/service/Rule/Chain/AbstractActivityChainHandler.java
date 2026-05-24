package shirlin.ai.domain.Activity.service.Rule.Chain;

import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.service.IActivityChainHandler;

public abstract class AbstractActivityChainHandler implements IActivityChainHandler {

    private IActivityChainHandler next;

    @Override
    public IActivityChainHandler appendNext(IActivityChainHandler next) {
        this.next = next;
        return next;   // 返回 next，支持链式: a.appendNext(b).appendNext(c)
    }

    @Override
    public boolean next(ActivityFactorEntity factor) {
        if (next == null) return true;   // 链尾，全部通过
        return next.apply(factor);
    }

}
