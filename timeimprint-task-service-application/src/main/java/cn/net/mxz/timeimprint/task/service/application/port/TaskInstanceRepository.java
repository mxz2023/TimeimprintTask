package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskInstanceSnapshot;
import java.util.List;
import java.util.Optional;

public interface TaskInstanceRepository {

    Optional<MxzTaskInstanceSnapshot> findById(long instanceId);

    Optional<MxzTaskInstanceSnapshot> findByIdForUpdate(long instanceId);

    List<MxzTaskInstanceSnapshot> listByDefinition(long definitionId);
}
