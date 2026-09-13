package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskInstanceSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskInstanceRepository {

    Optional<MxzTaskInstanceSnapshot> findById(long instanceId);

    Optional<MxzTaskInstanceSnapshot> findByIdForUpdate(long instanceId);

    List<MxzTaskInstanceSnapshot> listByDefinition(long definitionId);

    /**
     * E08: list instances for tenant with optional filters.
     * {@code cursorOccurrenceAt}/{@code cursorInstanceId} are a frozen keyset boundary.
     */
    List<MxzTaskInstanceSnapshot> list(
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
