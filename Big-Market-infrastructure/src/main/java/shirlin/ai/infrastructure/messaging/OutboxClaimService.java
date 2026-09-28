package shirlin.ai.infrastructure.messaging;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.infrastructure.dao.IOutboxEventDao;
import shirlin.ai.infrastructure.dao.po.OutboxEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxClaimService {
    private final IOutboxEventDao eventDao;

    public OutboxClaimService(IOutboxEventDao eventDao) {
        this.eventDao = eventDao;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<OutboxEvent> claimBatch(String workerId, int batchSize, Duration lease) {
        int limit = Math.max(1, Math.min(batchSize, 500));
        String token = UUID.randomUUID().toString();
        Date lockedUntil = Date.from(Instant.now().plus(lease));
        return eventDao.selectClaimable(limit).stream()
                .filter(event -> eventDao.acquireLease(
                        event.getEventId(), workerId, token, lockedUntil) == 1)
                .peek(event -> {
                    event.setLockedBy(workerId);
                    event.setLockToken(token);
                    event.setLockedUntil(lockedUntil);
                })
                .toList();
    }
}
