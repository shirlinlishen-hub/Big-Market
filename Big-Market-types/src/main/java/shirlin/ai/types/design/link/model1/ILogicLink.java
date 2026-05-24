package shirlin.ai.types.design.link.model1;

public interface ILogicLink<T, D, R> extends ILogicChainArmory<T, D, R> {
    R apply(T var1, D var2) throws Exception;
}