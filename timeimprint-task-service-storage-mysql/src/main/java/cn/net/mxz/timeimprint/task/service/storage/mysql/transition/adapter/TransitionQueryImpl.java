package cn.net.mxz.timeimprint.task.service.storage.mysql.transition.adapter;

import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.transition.model.TransitionRecord;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionQuery;
import cn.net.mxz.timeimprint.task.service.storage.mysql.shared.time.StorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.transition.mapper.TaskTransitionMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class TransitionQueryImpl implements TransitionQuery {

    private final TaskTransitionMapper mapper;

    public TransitionQueryImpl(TaskTransitionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<TransitionRecord> list(Long definitionId, Long instanceId, String cursor, int limit) {
        Long cursorId = parseCursor(cursor);
        return mapper.selectByDefinitionId(definitionId, instanceId, limit, cursorId).stream()
                .map(r -> new TransitionRecord(
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
                        StorageTime.toInstant(r.getCreatedAt())))
                .toList();
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new ApplicationException("INVALID_CURSOR", "cursor");
        }
    }
}
