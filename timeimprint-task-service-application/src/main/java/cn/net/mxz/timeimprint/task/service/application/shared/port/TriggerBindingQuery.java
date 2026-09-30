package cn.net.mxz.timeimprint.task.service.application.shared.port;

import cn.net.mxz.timeimprint.task.service.application.shared.model.TriggerBindingRecord;
import java.util.List;
import java.util.Optional;

public interface TriggerBindingQuery {

    List<TriggerBindingRecord> listByDefinition(long definitionId);

    Optional<TriggerBindingRecord> findById(long triggerBindingId);

    Optional<TriggerBindingRecord> findByIdForUpdate(long triggerBindingId);
}
