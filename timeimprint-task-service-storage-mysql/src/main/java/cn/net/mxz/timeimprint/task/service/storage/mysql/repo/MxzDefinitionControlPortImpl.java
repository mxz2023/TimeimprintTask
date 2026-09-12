package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.common.MxzSha256;
import cn.net.mxz.timeimprint.task.service.application.port.DefinitionControlPort;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzStorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskInstanceMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskSignalMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TriggerBindingMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

/**
 * Cancels WAITING instances and their planned signals for all trigger bindings
 * when a definition is paused or retired.
 */
@Repository
public class MxzDefinitionControlPortImpl implements DefinitionControlPort {

    private final TriggerBindingMapper triggerMapper;
    private final TaskInstanceMapper instanceMapper;
    private final TaskSignalMapper signalMapper;

    public MxzDefinitionControlPortImpl(
            TriggerBindingMapper triggerMapper,
            TaskInstanceMapper instanceMapper,
            TaskSignalMapper signalMapper) {
        this.triggerMapper = triggerMapper;
        this.instanceMapper = instanceMapper;
        this.signalMapper = signalMapper;
    }

    @Override
    public void cancelWindowAndSignals(long definitionId, Instant now) {
        LocalDateTime nowLdt = MxzStorageTime.toUtcLdt(now);
        String cancelledJson = "{\"scenarioState\":\"CANCELLED\"}";
        byte[] cancelledHash = MxzSha256.digestUtf8(cancelledJson);

        var bindings = triggerMapper.selectByDefinitionId(definitionId);
        for (var binding : bindings) {
            long bindingId = binding.getTriggerBindingId();
            long schedGen = binding.getScheduleGeneration() == null ? 1L : binding.getScheduleGeneration();

            // Cancel WAITING instances for this binding/generation
            instanceMapper.cancelWaitingInstances(
                    definitionId, bindingId, schedGen,
                    "CANCELLED", cancelledJson, cancelledHash,
                    nowLdt, nowLdt);

            // Ignore READY future signals for this binding/generation
            signalMapper.ignoreByBindingGeneration(
                    definitionId, bindingId, schedGen,
                    "CONTROL_STATE_CHANGE", nowLdt, nowLdt);
        }
    }
}
