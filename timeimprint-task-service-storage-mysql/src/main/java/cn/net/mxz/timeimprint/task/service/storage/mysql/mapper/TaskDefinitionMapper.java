package cn.net.mxz.timeimprint.task.service.storage.mysql.mapper;

import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskDefinitionRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskDefinitionMapper {

    void insert(TaskDefinitionRow row);

    TaskDefinitionRow selectById(@Param("definitionId") long definitionId);

    TaskDefinitionRow selectByIdForUpdate(@Param("definitionId") long definitionId);

    int updateRevision(@Param("definitionId") long definitionId,
                       @Param("expectedRevision") long expectedRevision,
                       @Param("newRevision") long newRevision,
                       @Param("controlState") String controlState,
                       @Param("controlGeneration") long controlGeneration,
                       @Param("updatedBy") String updatedBy,
                       @Param("updatedAt") java.time.LocalDateTime updatedAt,
                       @Param("pausedAt") java.time.LocalDateTime pausedAt,
                       @Param("retiredAt") java.time.LocalDateTime retiredAt);

    List<TaskDefinitionRow> selectList(@Param("tenantId") String tenantId,
                                       @Param("scenarioKey") String scenarioKey,
                                       @Param("controlState") String controlState,
                                       @Param("cursor") Long cursor,
                                       @Param("limit") int limit);
}
