package shirlin.ai.domain.Activity.service;

import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;

/**
 * 活动校验责任链节点接口
 * 每个节点通过 appendNext 形成单向链表；apply 返回 true 表示通过本节点并继续；
 * false / 抛异常 表示本节点拦截。
 */
public interface IActivityChainHandler {

    /** 当前节点处理逻辑 */
    boolean apply(ActivityFactorEntity factor);

    /** 追加下一个节点，返回被追加节点（方便链式调用） */
    IActivityChainHandler appendNext(IActivityChainHandler next);

    /** 调用下一个节点；链尾时直接返回 true */
    boolean next(ActivityFactorEntity factor);

}
