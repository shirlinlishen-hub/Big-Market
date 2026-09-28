package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.UserPointsLedger;

@Mapper
public interface IUserPointsDao {
    UserPointsLedger selectLedgerForUpdate(@Param("orderId") String orderId);

    int insertLedger(@Param("orderId") String orderId, @Param("userId") String userId,
                     @Param("points") int points);
    int addPoints(@Param("userId") String userId, @Param("points") int points);
}
