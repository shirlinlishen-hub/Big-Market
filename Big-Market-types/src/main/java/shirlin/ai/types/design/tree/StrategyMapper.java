package shirlin.ai.types.design.tree;

public interface StrategyMapper<T, D, R> {
    StrategyHandler<T, D, R> get(T var1, D var2) throws Exception;
}
