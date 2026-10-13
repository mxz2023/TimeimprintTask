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
     * E08：只列出当前参与人在该租户下可见的实例。没有可见实例时返回空列表。
     * {@code cursorOccurrenceAt}/{@code cursorInstanceId} 是稳定的键集边界。
     */
    List<TaskInstanceSnapshot> list(
            String tenantId,
            String participantPrincipalId,
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
