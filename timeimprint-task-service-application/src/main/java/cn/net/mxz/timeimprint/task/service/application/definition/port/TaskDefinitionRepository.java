package cn.net.mxz.timeimprint.task.service.application.definition.port;

import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskDefinitionRepository {

    Optional<TaskDefinitionSnapshot> findById(long definitionId);

    Optional<TaskDefinitionSnapshot> findByIdForUpdate(long definitionId);

    /**
     * E05: list definitions for tenant with optional filters.
     * {@code cursorUpdatedAt}/{@code cursorDefinitionId} are a frozen keyset boundary (both null = first page).
     */
    List<TaskDefinitionSnapshot> list(
            String tenantId,
            String scenarioKey,
            String controlState,
            Instant cursorUpdatedAt,
            Long cursorDefinitionId,
            int limit);
}
