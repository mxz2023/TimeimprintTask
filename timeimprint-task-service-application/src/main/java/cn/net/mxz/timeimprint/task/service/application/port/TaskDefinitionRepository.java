package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskDefinitionSnapshot;
import java.util.Optional;

public interface TaskDefinitionRepository {

    Optional<MxzTaskDefinitionSnapshot> findById(long definitionId);

    Optional<MxzTaskDefinitionSnapshot> findByIdForUpdate(long definitionId);
}
