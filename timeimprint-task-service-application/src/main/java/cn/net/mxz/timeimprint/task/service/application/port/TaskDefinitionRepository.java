package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskDefinitionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskDefinitionRepository {

    Optional<MxzTaskDefinitionSnapshot> findById(long definitionId);

    Optional<MxzTaskDefinitionSnapshot> findByIdForUpdate(long definitionId);

    /**
     * E05: list definitions for tenant with optional filters.
     * {@code cursorUpdatedAt}/{@code cursorDefinitionId} are a frozen keyset boundary (both null = first page).
     */
    List<MxzTaskDefinitionSnapshot> list(
            String tenantId,
            String scenarioKey,
            String controlState,
            Instant cursorUpdatedAt,
            Long cursorDefinitionId,
            int limit);
}
