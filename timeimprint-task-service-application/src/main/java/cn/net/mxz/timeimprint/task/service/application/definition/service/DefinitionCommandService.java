package cn.net.mxz.timeimprint.task.service.application.definition.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

/**
 * E06: Definition-level commands: update / pause / resume / retire.
 * Delegates update vs control paths to dedicated executors.
 */
@Service
public class DefinitionCommandService {

    private final DefinitionUpdateExecutor updateExecutor;
    private final DefinitionControlExecutor controlExecutor;

    public DefinitionCommandService(
            DefinitionUpdateExecutor updateExecutor, DefinitionControlExecutor controlExecutor) {
        this.updateExecutor = updateExecutor;
        this.controlExecutor = controlExecutor;
    }

    public record CommandResult(String commandKey, long definitionId, long revision, boolean changed) {}

    public CommandResult execute(
            long definitionId,
            String commandKey,
            String requestId,
            long expectedRevision,
            int commandSchemaVersion,
            JsonNode payload) {
        if ("update".equals(commandKey)) {
            return updateExecutor.execute(definitionId, requestId, expectedRevision, commandSchemaVersion, payload);
        }
        return controlExecutor.execute(
                definitionId, commandKey, requestId, expectedRevision, commandSchemaVersion, payload);
    }
}
