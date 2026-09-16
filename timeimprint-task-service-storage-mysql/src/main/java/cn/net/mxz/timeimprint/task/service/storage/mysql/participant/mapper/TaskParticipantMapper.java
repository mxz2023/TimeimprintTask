package cn.net.mxz.timeimprint.task.service.storage.mysql.participant.mapper;

import cn.net.mxz.timeimprint.task.service.storage.mysql.participant.row.TaskParticipantRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskParticipantMapper {

    void insert(TaskParticipantRow row);

    void insertBatch(@Param("rows") List<TaskParticipantRow> rows);

    List<TaskParticipantRow> selectByDefinitionId(@Param("definitionId") long definitionId,
                                                  @Param("instanceId") Long instanceId);

    void deleteByDefinitionIdDefinitionLevel(@Param("definitionId") long definitionId);
}
