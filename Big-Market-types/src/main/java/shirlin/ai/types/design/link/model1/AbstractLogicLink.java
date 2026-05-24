package shirlin.ai.types.design.link.model1;

public abstract class AbstractLogicLink<T, D, R> implements ILogicLink<T, D, R> {
    private ILogicLink<T, D, R> next;

    public ILogicLink<T, D, R> next() {
        return this.next;
    }

    public ILogicLink<T, D, R> appendNext(ILogicLink<T, D, R> next) {
        this.next = next;
        return next;
    }

    protected R next(T requestParameter, D dynamicContext) throws Exception {
        return (R)this.next.apply(requestParameter, dynamicContext);
    }
}