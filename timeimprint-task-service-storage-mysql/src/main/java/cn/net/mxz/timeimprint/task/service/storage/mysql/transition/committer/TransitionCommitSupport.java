package cn.net.mxz.timeimprint.task.service.storage.mysql.transition.committer;

import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioMutationPayload;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.DefinitionControlTransition;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.InstanceStateTransition;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionCommitRequest;
import cn.net.mxz.timeimprint.task.service.storage.mysql.definition.row.TaskDefinitionRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.instance.row.TaskInstanceRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.transition.row.TaskTransitionRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Transition commit codecs and transition-row factory.
 * Change reason: JSON/time encoding and transition audit rows independent of side-effect inserts.
 */
@Component
public class TransitionCommitSupport {

    private final ObjectMapper objectMapper;

    public TransitionCommitSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    TaskTransitionRow buildInstanceTransitionRow(TransitionCommitRequest request,
            TaskDefinitionRow defRow, TaskInstanceRow instRow, LocalDateTime now) {
        InstanceStateTransition ist = request.plan().instanceStateTransition();
        TaskTransitionRow tr = new TaskTransitionRow();
        tr.setDefinitionId(request.definitionId());
        tr.setInstanceId(request.instanceId());
        tr.setSourceType(request.sourceType());
        tr.setSourceKey(request.sourceKey());
        tr.setCommandKey("COMMAND".equals(request.sourceType()) ? request.sourceKey() : null);
        tr.setFromControlState(null);
        tr.setToControlState(null);
        tr.setFromLifecycle(ist != null ? ist.fromLifecycleCategory().name() : instRow.getLifecycleCategory());
        tr.setToLifecycle(ist != null ? ist.toLifecycleCategory().name() : instRow.getLifecycleCategory());
        tr.setFromScenarioState(ist != null ? ist.fromScenarioState() : instRow.getScenarioState());
        tr.setToScenarioState(ist != null ? ist.toScenarioState() : instRow.getScenarioState());
        tr.setFromRevision(instRow.getRevision());
        tr.setToRevision(instRow.getRevision() + 1);
        tr.setActorType("SYSTEM");
        tr.setActorId("system");
        tr.setSummaryJson("{\"source\":\"" + request.sourceKey() + "\"}");
        tr.setTraceId(java.util.UUID.randomUUID().toString().replace("-", ""));
        tr.setCreatedAt(now);
        return tr;
    }

    TaskTransitionRow buildDefinitionTransitionRow(TransitionCommitRequest request,
            TaskDefinitionRow defRow, LocalDateTime now) {
        DefinitionControlTransition dct = request.plan().definitionControlTransition();
        String fromState = dct != null ? dct.fromControlState().name() : defRow.getControlState();
        String toState = dct != null ? dct.toControlState().name() : defRow.getControlState();
        TaskTransitionRow tr = new TaskTransitionRow();
        tr.setDefinitionId(request.definitionId());
        tr.setInstanceId(null);
        tr.setSourceType(request.sourceType());
        tr.setSourceKey(request.sourceKey());
        tr.setCommandKey("COMMAND".equals(request.sourceType()) ? request.sourceKey() : null);
        tr.setFromControlState(fromState);
        tr.setToControlState(toState);
        tr.setFromLifecycle(null);
        tr.setToLifecycle(null);
        tr.setFromScenarioState(null);
        tr.setToScenarioState(null);
        tr.setFromRevision(defRow.getRevision());
        tr.setToRevision(defRow.getRevision() + 1);
        tr.setActorType("SYSTEM");
        tr.setActorId("system");
        tr.setSummaryJson("{\"source\":\"" + request.sourceKey() + "\"}");
        tr.setTraceId(java.util.UUID.randomUUID().toString().replace("-", ""));
        tr.setCreatedAt(now);
        return tr;
    }

    String toJson(ScenarioMutationPayload payload) {
        if (payload == null) return "{}";
        if (payload instanceof JsonPayload jp) {
            return toJsonFromMap(jp.fields());
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    String toJsonFromMap(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    LocalDateTime toLocal(Instant instant) {
        if (instant == null) return null;
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    String toString(Object o, String defaultVal) {
        return o != null ? String.valueOf(o) : defaultVal;
    }

    String jsonStringEscape(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
