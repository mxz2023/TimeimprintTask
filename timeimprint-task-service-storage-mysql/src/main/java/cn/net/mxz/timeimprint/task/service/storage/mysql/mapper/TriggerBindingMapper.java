package cn.net.mxz.timeimprint.task.service.storage.mysql.mapper;

import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TriggerBindingRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TriggerBindingMapper {

    void insert(TriggerBindingRow row);

    TriggerBindingRow selectById(@Param("triggerBindingId") long triggerBindingId);

    TriggerBindingRow selectByIdForUpdate(@Param("triggerBindingId") long triggerBindingId);

    List<TriggerBindingRow> selectByDefinitionId(@Param("definitionId") long definitionId);

    int updateCursor(@Param("triggerBindingId") long triggerBindingId,
                     @Param("expectedRevision") long expectedRevision,
                     @Param("newRevision") long newRevision,
                     @Param("nextFireAt") LocalDateTime nextFireAt,
                     @Param("cursorJson") String cursorJson,
                     @Param("exhausted") boolean exhausted,
                     @Param("updatedAt") LocalDateTime updatedAt);

    int updateConfigAndSchedule(@Param("triggerBindingId") long triggerBindingId,
                                @Param("expectedRevision") long expectedRevision,
                                @Param("newRevision") long newRevision,
                                @Param("configJson") String configJson,
                                @Param("configHash") byte[] configHash,
                                @Param("scheduleGeneration") long scheduleGeneration,
                                @Param("nextFireAt") LocalDateTime nextFireAt,
                                @Param("cursorJson") String cursorJson,
                                @Param("exhausted") boolean exhausted,
                                @Param("updatedAt") LocalDateTime updatedAt);

    /** Planner: SKIP LOCKED scan for due bindings. */
    List<Long> selectDueTriggerIds(@Param("providerKey") String providerKey,
                                   @Param("now") LocalDateTime now,
                                   @Param("limit") int limit);
}
