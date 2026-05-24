package shirlin.ai.types.design.tree;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.Generated;

public abstract class AbstractMultiThreadStrategyRouter<T, D, R> implements StrategyMapper<T, D, R>, StrategyHandler<T, D, R> {
    protected StrategyHandler<T, D, R> defaultStrategyHandler;

    public AbstractMultiThreadStrategyRouter() {
        this.defaultStrategyHandler = StrategyHandler.DEFAULT;
    }

    public R router(T requestParameter, D dynamicContext) throws Exception {
        StrategyHandler<T, D, R> strategyHandler = this.get(requestParameter, dynamicContext);
        return (R)(null != strategyHandler ? strategyHandler.apply(requestParameter, dynamicContext) : this.defaultStrategyHandler.apply(requestParameter, dynamicContext));
    }

    public R apply(T requestParameter, D dynamicContext) throws Exception {
        this.multiThread(requestParameter, dynamicContext);
        return (R)this.doApply(requestParameter, dynamicContext);
    }

    protected abstract void multiThread(T var1, D var2) throws ExecutionException, InterruptedException, TimeoutException;

    protected abstract R doApply(T var1, D var2) throws Exception;

    @Generated
    public StrategyHandler<T, D, R> getDefaultStrategyHandler() {
        return this.defaultStrategyHandler;
    }

    @Generated
    public void setDefaultStrategyHandler(StrategyHandler<T, D, R> defaultStrategyHandler) {
        this.defaultStrategyHandler = defaultStrategyHandler;
    }
}
