package cn.net.mxz.timeimprint.task.service.storage.mysql.mapper;

import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskInstanceRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskInstanceMapper {

    void insert(TaskInstanceRow row);

    TaskInstanceRow selectById(@Param("instanceId") long instanceId);

    TaskInstanceRow selectByIdForUpdate(@Param("instanceId") long instanceId);

    int updateRevision(@Param("instanceId") long instanceId,
                       @Param("expectedRevision") long expectedRevision,
                       @Param("newRevision") long newRevision,
                       @Param("lifecycleCategory") String lifecycleCategory,
                       @Param("scenarioState") String scenarioState,
                       @Param("scenarioSnapshotJson") String scenarioSnapshotJson,
                       @Param("snapshotHash") byte[] snapshotHash,
                       @Param("terminalAt") LocalDateTime terminalAt,
                       @Param("updatedAt") LocalDateTime updatedAt);

    List<TaskInstanceRow> selectByDefinitionId(@Param("definitionId") long definitionId,
                                               @Param("lifecycleCategory") String lifecycleCategory,
                                               @Param("scenarioState") String scenarioState,
                                               @Param("cursor") String cursor,
                                               @Param("limit") int limit);

    List<TaskInstanceRow> selectByDefinitionIdAll(@Param("definitionId") long definitionId);

    List<TaskInstanceRow> selectList(@Param("tenantId") String tenantId,
                                     @Param("definitionId") Long definitionId,
                                     @Param("scenarioKey") String scenarioKey,
                                     @Param("lifecycleCategory") String lifecycleCategory,
                                     @Param("scenarioState") String scenarioState,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to,
                                     @Param("cursorOccurrenceAt") LocalDateTime cursorOccurrenceAt,
                                     @Param("cursorInstanceId") Long cursorInstanceId,
                                     @Param("limit") int limit);

    /** Cancel waiting instances for a given binding/scheduleGeneration (pause/retire/update). */
    int cancelWaitingInstances(@Param("definitionId") long definitionId,
                               @Param("triggerBindingId") long triggerBindingId,
                               @Param("scheduleGeneration") long scheduleGeneration,
                               @Param("scenarioState") String cancelledScenarioState,
                               @Param("snapshotJson") String snapshotJson,
                               @Param("snapshotHash") byte[] snapshotHash,
                               @Param("terminalAt") LocalDateTime terminalAt,
                               @Param("updatedAt") LocalDateTime updatedAt);

    /** Bump WAITING title/description snapshots after non-calendar definition update. */
    int bumpWaitingSnapshots(@Param("definitionId") long definitionId,
                             @Param("title") String title,
                             @Param("description") String description,
                             @Param("updatedAt") LocalDateTime updatedAt);
}
