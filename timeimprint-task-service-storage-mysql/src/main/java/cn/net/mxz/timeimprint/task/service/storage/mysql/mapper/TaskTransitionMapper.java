package cn.net.mxz.timeimprint.task.service.storage.mysql.mapper;

import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskTransitionRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskTransitionMapper {

    void insert(TaskTransitionRow row);

    TaskTransitionRow selectById(@Param("transitionId") long transitionId);

    List<TaskTransitionRow> selectByDefinitionId(@Param("definitionId") Long definitionId,
                                                 @Param("instanceId") Long instanceId,
                                                 @Param("limit") int limit,
                                                 @Param("cursor") Long cursor);
}
