package shirlin.ai.types.design.link.model2.handler;

public interface ILogicHandler<T, D, R> {

    default R next(T requestParameter, D dynamicContext) {
        return null;
    }

    R apply(T var1, D var2) throws Exception;
}
