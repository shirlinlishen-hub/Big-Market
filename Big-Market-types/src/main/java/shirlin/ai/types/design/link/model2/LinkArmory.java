package shirlin.ai.types.design.link.model2;

import shirlin.ai.types.design.link.model2.chain.BusinessLinkedList;
import shirlin.ai.types.design.link.model2.handler.ILogicHandler;

public class LinkArmory<T, D, R> {
    private final BusinessLinkedList<T, D, R> logicLink;

    @SafeVarargs
    public LinkArmory(String linkName, ILogicHandler<T, D, R>... logicHandlers) {
        this.logicLink = new BusinessLinkedList(linkName);

        for(ILogicHandler<T, D, R> logicHandler : logicHandlers) {
            this.logicLink.add(logicHandler);
        }

    }

    public BusinessLinkedList<T, D, R> getLogicLink() {
        return this.logicLink;
    }
}
