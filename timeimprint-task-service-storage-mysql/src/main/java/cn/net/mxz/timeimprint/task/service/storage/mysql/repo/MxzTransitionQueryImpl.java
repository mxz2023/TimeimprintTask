package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzTransitionRecord;
import cn.net.mxz.timeimprint.task.service.application.port.TransitionQuery;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzStorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskTransitionMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MxzTransitionQueryImpl implements TransitionQuery {

    private final TaskTransitionMapper mapper;

    public MxzTransitionQueryImpl(TaskTransitionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<MxzTransitionRecord> list(Long definitionId, Long instanceId, String cursor, int limit) {
        Long cursorId = parseCursor(cursor);
        return mapper.selectByDefinitionId(definitionId, instanceId, limit, cursorId).stream()
                .map(r -> new MxzTransitionRecord(
                        r.getTransitionId(),
                        r.getDefinitionId(),
                        r.getInstanceId(),
                        r.getSourceType(),
                        r.getSourceKey(),
                        r.getCommandKey(),
                        r.getFromControlState(),
                        r.getToControlState(),
                        r.getFromLifecycle(),
                        r.getToLifecycle(),
                        r.getFromScenarioState(),
                        r.getToScenarioState(),
                        r.getFromRevision() == null ? 0L : r.getFromRevision(),
                        r.getToRevision() == null ? 0L : r.getToRevision(),
                        r.getActorType(),
                        r.getActorId(),
                        r.getTraceId(),
                        MxzStorageTime.toInstant(r.getCreatedAt())))
                .toList();
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new MxzApplicationException("INVALID_CURSOR", "cursor");
        }
    }
}
