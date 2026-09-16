package cn.net.mxz.timeimprint.task.service.application.instance.port;

import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskInstanceRepository {

    Optional<TaskInstanceSnapshot> findById(long instanceId);

    Optional<TaskInstanceSnapshot> findByIdForUpdate(long instanceId);

    List<TaskInstanceSnapshot> listByDefinition(long definitionId);

    /**
     * E08: list instances for tenant with optional filters.
     * {@code cursorOccurrenceAt}/{@code cursorInstanceId} are a frozen keyset boundary.
     */
    List<TaskInstanceSnapshot> list(
            String tenantId,
            Long definitionId,
            String scenarioKey,
            String lifecycleCategory,
            String scenarioState,
            Instant from,
            Instant to,
            Instant cursorOccurrenceAt,
            Long cursorInstanceId,
            int limit);
}
