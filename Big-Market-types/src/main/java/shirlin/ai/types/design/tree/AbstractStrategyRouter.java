package shirlin.ai.types.design.tree;


import lombok.Generated;

public abstract class AbstractStrategyRouter<T, D, R> implements StrategyMapper<T, D, R>, StrategyHandler<T, D, R> {
    protected StrategyHandler<T, D, R> defaultStrategyHandler;

    public AbstractStrategyRouter() {
        this.defaultStrategyHandler = StrategyHandler.DEFAULT;
    }

    public R router(T requestParameter, D dynamicContext) throws Exception {
        StrategyHandler<T, D, R> strategyHandler = this.get(requestParameter, dynamicContext);
        return (R)(null != strategyHandler ? strategyHandler.apply(requestParameter, dynamicContext) : this.defaultStrategyHandler.apply(requestParameter, dynamicContext));
    }

    @Generated
    public StrategyHandler<T, D, R> getDefaultStrategyHandler() {
        return this.defaultStrategyHandler;
    }

    @Generated
    public void setDefaultStrategyHandler(StrategyHandler<T, D, R> defaultStrategyHandler) {
        this.defaultStrategyHandler = defaultStrategyHandler;
    }
}
