package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import shirlin.ai.domain.strategy.model.entity.AwardRateRange;
import shirlin.ai.infrastructure.adapter.repository.StrategyRepository;
import shirlin.ai.infrastructure.dao.IStrategyAwardDao;
import shirlin.ai.infrastructure.dao.po.StrategyAward;
import shirlin.ai.infrastructure.redis.IRedisService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ExcludedPoolRefreshTest {
    @Test
    void excludedPoolDoesNotUseStaleRatesAfterStrategyChanges() {
        StrategyRepository repository = new StrategyRepository();
        IStrategyAwardDao awards = mock(IStrategyAwardDao.class);
        IRedisService redis = mock(IRedisService.class);
        ReflectionTestUtils.setField(repository, "strategyAwardDao", awards);
        ReflectionTestUtils.setField(repository, "redisService", redis);
        Map<String, Object> cache = new ConcurrentHashMap<>();
        cache.put("big_market:strategy:precision:1", 100);
        when(redis.getValue(anyString())).thenAnswer(call -> cache.get(call.getArgument(0)));
        doAnswer(call -> {
            cache.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(redis).setValue(anyString(), any());
        when(awards.selectByStrategyId(1L)).thenReturn(
                List.of(award(101, "0.5"), award(102, "0.5")),
                List.of(award(101, "0.2"), award(102, "0.8")));

        List<AwardRateRange> first = repository.buildSubRangeTable(1L, Set.of(101));
        List<AwardRateRange> refreshed = repository.buildSubRangeTable(1L, Set.of(101));

        assertEquals(50, first.get(0).getRangeEnd());
        assertEquals(80, refreshed.get(0).getRangeEnd());
    }

    private static StrategyAward award(int id, String rate) {
        StrategyAward result = new StrategyAward();
        result.setStrategyId(1L); result.setAwardId(id);
        result.setAwardRate(new BigDecimal(rate));
        return result;
    }
}
