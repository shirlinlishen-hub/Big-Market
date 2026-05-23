package shirlin.ai.domain.strategy.service.Rule.Factory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.service.Rule.IStrategyLogicFilterService;
import shirlin.ai.types.exception.AppException;

import java.util.Map;

/**
 * 规则过滤器工厂
 * Spring 自动将所有 IStrategyLogicFilterService 实现以 beanName 为 key 注入 filterMap
 */
@Service
public class DefaultLogicFactory {

    @Autowired
    private Map<String, IStrategyLogicFilterService> filterMap;

    public IStrategyLogicFilterService getFilter(String ruleBeanName) {
        IStrategyLogicFilterService filter = filterMap.get(ruleBeanName);
        if (filter == null) {
            throw new AppException("未找到规则过滤器: " + ruleBeanName);
        }
        return filter;
    }

}
