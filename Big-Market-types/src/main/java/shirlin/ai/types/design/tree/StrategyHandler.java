package shirlin.ai.types.design.tree;

public interface StrategyHandler<T, D, R> {
    StrategyHandler DEFAULT = (T, D) -> null;

    R apply(T var1, D var2) throws Exception;
}
