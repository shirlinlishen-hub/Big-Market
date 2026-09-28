package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.OutboxEvent;

import java.util.Date;
import java.util.List;

@Mapper
public interface IOutboxEventDao {
    int insertIdempotent(OutboxEvent event);
    List<OutboxEvent> selectClaimable(@Param("limit") Integer limit);
    int acquireLease(@Param("eventId") String eventId,
                     @Param("lockedBy") String lockedBy,
                     @Param("lockToken") String lockToken,
                     @Param("lockedUntil") Date lockedUntil);
    int markPublished(@Param("eventId") String eventId,
                      @Param("lockToken") String lockToken);
    int recordPublishFailure(@Param("eventId") String eventId,
                             @Param("lockToken") String lockToken,
                             @Param("reason") String reason);
}
