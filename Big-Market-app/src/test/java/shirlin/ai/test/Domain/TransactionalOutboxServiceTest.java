package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import shirlin.ai.infrastructure.adapter.repository.TransactionalOutboxService;
import shirlin.ai.infrastructure.dao.IOutboxEventDao;
import shirlin.ai.infrastructure.dao.po.OutboxEvent;
import shirlin.ai.types.Tool.SnowflakeIdGenerator;

import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class TransactionalOutboxServiceTest {
    @Test
    void appendUsesStableEventKeyAndAcceptsIdempotentReplay() {
        IOutboxEventDao dao = mock(IOutboxEventDao.class);
        SnowflakeIdGenerator ids = mock(SnowflakeIdGenerator.class);
        when(ids.nextId()).thenReturn(1001L, 1002L);
        when(dao.insertIdempotent(any())).thenReturn(1, 0);
        TransactionalOutboxService service = new TransactionalOutboxService();
        ReflectionTestUtils.setField(service, "eventDao", dao);
        ReflectionTestUtils.setField(service, "idGenerator", ids);

        service.append("AWARD_DELIVERY_REQUESTED", "award-delivery:draw-1", "draw-1",
                "user-1", Map.of("orderId", "draw-1"));
        service.append("AWARD_DELIVERY_REQUESTED", "award-delivery:draw-1", "draw-1",
                "user-1", Map.of("orderId", "draw-1"));

        verify(dao, times(2)).insertIdempotent(argThat((OutboxEvent e) ->
                "award-delivery:draw-1".equals(e.getEventKey())
                        && "user-1".equals(e.getPartitionKey())
                        && e.getPayload().contains("draw-1")));
    }
}
