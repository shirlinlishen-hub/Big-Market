package shirlin.ai.test.Infrastructure;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import shirlin.ai.infrastructure.dao.IOutboxEventDao;
import shirlin.ai.infrastructure.dao.po.OutboxEvent;
import shirlin.ai.infrastructure.messaging.OutboxClaimService;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxClaimServiceTest {

    @Test
    void boundsBatchSizeBetweenOneAndFiveHundred() {
        IOutboxEventDao dao = mock(IOutboxEventDao.class);
        when(dao.selectClaimable(anyInt())).thenReturn(List.of());
        OutboxClaimService service = new OutboxClaimService(dao);

        service.claimBatch("worker-1", 0, Duration.ofSeconds(30));
        service.claimBatch("worker-1", 900, Duration.ofSeconds(30));

        verify(dao).selectClaimable(1);
        verify(dao).selectClaimable(500);
        verifyNoMoreInteractions(dao);
    }

    @Test
    void emptySelectionDoesNotAttemptToAcquireLeases() {
        IOutboxEventDao dao = mock(IOutboxEventDao.class);
        when(dao.selectClaimable(100)).thenReturn(List.of());
        OutboxClaimService service = new OutboxClaimService(dao);

        assertTrue(service.claimBatch("worker-1", 100, Duration.ofSeconds(30)).isEmpty());

        verify(dao).selectClaimable(100);
        verifyNoMoreInteractions(dao);
    }

    @Test
    void returnsOnlyAcquiredRowsWithOneBatchTokenAndThirtySecondLease() {
        IOutboxEventDao dao = mock(IOutboxEventDao.class);
        OutboxEvent first = event("event-1");
        OutboxEvent lostRace = event("event-2");
        OutboxEvent third = event("event-3");
        when(dao.selectClaimable(3)).thenReturn(List.of(first, lostRace, third));
        when(dao.acquireLease(eq("event-1"), eq("worker-1"), anyString(), any(Date.class)))
                .thenReturn(1);
        when(dao.acquireLease(eq("event-2"), eq("worker-1"), anyString(), any(Date.class)))
                .thenReturn(0);
        when(dao.acquireLease(eq("event-3"), eq("worker-1"), anyString(), any(Date.class)))
                .thenReturn(1);
        OutboxClaimService service = new OutboxClaimService(dao);
        Instant before = Instant.now();

        List<OutboxEvent> claimed = service.claimBatch(
                "worker-1", 3, Duration.ofSeconds(30));

        Instant after = Instant.now();
        assertEquals(List.of(first, third), claimed);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Date> leaseCaptor = ArgumentCaptor.forClass(Date.class);
        verify(dao, times(3)).acquireLease(anyString(), eq("worker-1"),
                tokenCaptor.capture(), leaseCaptor.capture());
        assertEquals(1, tokenCaptor.getAllValues().stream().distinct().count());
        assertDoesNotThrow(() -> java.util.UUID.fromString(tokenCaptor.getValue()));
        assertEquals(1, leaseCaptor.getAllValues().stream().distinct().count());
        Instant lockedUntil = leaseCaptor.getValue().toInstant();
        assertFalse(lockedUntil.isBefore(before.plusSeconds(29)));
        assertFalse(lockedUntil.isAfter(after.plusSeconds(31)));
        assertEquals("worker-1", first.getLockedBy());
        assertEquals(tokenCaptor.getValue(), first.getLockToken());
        assertEquals(lockedUntil, first.getLockedUntil().toInstant());
        assertNull(lostRace.getLockToken());
        assertEquals(tokenCaptor.getValue(), third.getLockToken());
    }

    private static OutboxEvent event(String eventId) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        return event;
    }
}
