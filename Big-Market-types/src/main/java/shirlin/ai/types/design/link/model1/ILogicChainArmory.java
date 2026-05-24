package shirlin.ai.types.design.link.model1;

/**
 * 单链条
 * @param <T>
 * @param <D>
 * @param <R>
 */
public interface ILogicChainArmory<T, D, R> {
    ILogicLink<T, D, R> next();

    ILogicLink<T, D, R> appendNext(ILogicLink<T, D, R> var1);
}
