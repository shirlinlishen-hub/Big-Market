package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.Award;

import java.util.List;

@Mapper
public interface IAwardDao {
    int insert(Award awardPO);

    int updateById(Award awardPO);

    int deleteById(Long id);

    Award selectById(Long id);

    Award selectByAwardId(Integer awardId);

    List<Award> selectAll();

    List<Award> selectByAwardIds(@Param("awardIds") List<Integer> awardIds);
}