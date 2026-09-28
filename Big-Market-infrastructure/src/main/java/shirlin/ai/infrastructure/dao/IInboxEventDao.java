package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.InboxEvent;

@Mapper
public interface IInboxEventDao {
    int insertOrTouch(InboxEvent event);

    InboxEvent selectForUpdate(@Param("consumerName") String consumerName,
                               @Param("eventId") String eventId);

    int markSuccess(@Param("consumerName") String consumerName,
                    @Param("eventId") String eventId);
}
