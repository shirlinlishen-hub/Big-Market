package shirlin.ai.types.design.link.model2.chain;

import shirlin.ai.types.design.link.model2.handler.ILogicHandler;

public class BusinessLinkedList<T, D, R> extends LinkedList<ILogicHandler<T, D, R>> implements ILogicHandler<T, D, R> {
    public BusinessLinkedList(String name) {
        super(name);
    }

    public R apply(T requestParameter, D dynamicContext) throws Exception {
        LinkedList.Node<ILogicHandler<T, D, R>> current = this.first;

        do {
            ILogicHandler<T, D, R> item = (ILogicHandler)current.item;
            R apply = (R)item.apply(requestParameter, dynamicContext);
            if (null != apply) {
                return apply;
            }

            current = current.next;
        } while(null != current);

        return null;
    }
}
