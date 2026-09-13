package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.application.model.MxzTriggerBindingRecord;
import java.util.List;
import java.util.Optional;

public interface TriggerBindingQuery {

    List<MxzTriggerBindingRecord> listByDefinition(long definitionId);

    Optional<MxzTriggerBindingRecord> findById(long triggerBindingId);

    Optional<MxzTriggerBindingRecord> findByIdForUpdate(long triggerBindingId);
}
