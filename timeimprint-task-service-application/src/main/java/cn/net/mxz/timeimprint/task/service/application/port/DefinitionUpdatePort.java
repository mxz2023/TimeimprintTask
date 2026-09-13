package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.application.model.MxzCreateDefinitionCommand;
import java.time.Instant;
import java.util.List;

/** Persist E06 definition update (full six-field replace). */
public interface DefinitionUpdatePort {

    record UpdateCommand(
            long definitionId,
            String tenantId,
            String actorType,
            String actorId,
            String requestId,
            String traceId,
            long expectedRevision,
            int scenarioSchemaVersion,
            String title,
            String description,
            String scenarioConfigJson,
            List<MxzCreateDefinitionCommand.ParticipantInput> participants,
            List<MxzCreateDefinitionCommand.TriggerBindingInput> triggerBindings,
            Instant now,
            Instant windowEnd) {}

    record UpdateResult(long revision, boolean changed, long scheduleGeneration) {}

    UpdateResult apply(UpdateCommand command);
}
