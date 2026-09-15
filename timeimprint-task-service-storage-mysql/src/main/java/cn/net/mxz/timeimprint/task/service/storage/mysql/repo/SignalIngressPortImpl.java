package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.common.Sha256;
import cn.net.mxz.timeimprint.task.service.application.exception.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.SignalAcceptCommand;
import cn.net.mxz.timeimprint.task.service.application.model.SignalAcceptResult;
import cn.net.mxz.timeimprint.task.service.application.port.SignalIngressPort;
import cn.net.mxz.timeimprint.task.service.storage.mysql.StorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskDefinitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskInstanceMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskSignalMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskSignalRow;
import java.util.Arrays;
import org.springframework.stereotype.Repository;

@Repository
public class SignalIngressPortImpl implements SignalIngressPort {

    private final TaskSignalMapper signalMapper;
    private final TaskDefinitionMapper definitionMapper;
    private final TaskInstanceMapper instanceMapper;

    public SignalIngressPortImpl(
            TaskSignalMapper signalMapper,
            TaskDefinitionMapper definitionMapper,
            TaskInstanceMapper instanceMapper) {
        this.signalMapper = signalMapper;
        this.definitionMapper = definitionMapper;
        this.instanceMapper = instanceMapper;
    }

    @Override
    public SignalAcceptResult accept(SignalAcceptCommand cmd) {
        var existing = signalMapper.selectBySourceKey(cmd.tenantId(), cmd.providerKey(), cmd.signalKey());
        byte[] hash = Sha256.digestUtf8(cmd.payloadJson());
        if (existing != null) {
            if (!Arrays.equals(existing.getPayloadHash(), hash)) {
                throw new ApplicationException("IDEMPOTENCY_CONFLICT", "signal key payload mismatch");
            }
            return new SignalAcceptResult(
                    existing.getSignalId(),
                    true,
                    existing.getProcessStatus(),
                    cn.net.mxz.timeimprint.task.service.storage.mysql.StorageTime.toInstant(
                            existing.getReceivedAt()));
        }
        var def = definitionMapper.selectById(cmd.definitionId());
        if (def == null || !cmd.tenantId().equals(def.getTenantId())) {
            throw new ApplicationException("RESOURCE_NOT_FOUND", "definition not found");
        }
        Long triggerBindingId = null;
        if (cmd.instanceId() != null) {
            var inst = instanceMapper.selectById(cmd.instanceId());
            if (inst == null || inst.getDefinitionId() != cmd.definitionId()) {
                throw new ApplicationException("RESOURCE_NOT_FOUND", "instance not found");
            }
            triggerBindingId = inst.getTriggerBindingId();
        }
        TaskSignalRow row = new TaskSignalRow();
        row.setTenantId(cmd.tenantId());
        row.setDefinitionId(cmd.definitionId());
        row.setTriggerBindingId(triggerBindingId);
        row.setInstanceId(cmd.instanceId());
        row.setDefinitionControlGeneration(def.getControlGeneration());
        row.setRedriveNo(0);
        row.setProviderKey(cmd.providerKey());
        row.setSignalKey(cmd.signalKey());
        row.setSchemaVersion(cmd.schemaVersion());
        row.setOccurredAt(StorageTime.toUtcLdt(cmd.occurredAt()));
        row.setReceivedAt(StorageTime.toUtcLdt(cmd.now()));
        row.setPayloadJson(cmd.payloadJson());
        row.setPayloadHash(hash);
        row.setProcessStatus("READY");
        row.setAttemptCount(0);
        row.setMaxAttempts(8);
        row.setNextAttemptAt(StorageTime.toUtcLdt(cmd.now()));
        row.setCreatedAt(StorageTime.toUtcLdt(cmd.now()));
        row.setUpdatedAt(StorageTime.toUtcLdt(cmd.now()));
        signalMapper.insert(row);
        return new SignalAcceptResult(row.getSignalId(), false, "READY", cmd.now());
    }
}
