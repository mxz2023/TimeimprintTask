package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.common.MxzSha256;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzSignalAcceptCommand;
import cn.net.mxz.timeimprint.task.service.application.model.MxzSignalAcceptResult;
import cn.net.mxz.timeimprint.task.service.application.port.SignalIngressPort;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzStorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskDefinitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskInstanceMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskSignalMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskSignalRow;
import java.util.Arrays;
import org.springframework.stereotype.Repository;

@Repository
public class MxzSignalIngressPortImpl implements SignalIngressPort {

    private final TaskSignalMapper signalMapper;
    private final TaskDefinitionMapper definitionMapper;
    private final TaskInstanceMapper instanceMapper;

    public MxzSignalIngressPortImpl(
            TaskSignalMapper signalMapper,
            TaskDefinitionMapper definitionMapper,
            TaskInstanceMapper instanceMapper) {
        this.signalMapper = signalMapper;
        this.definitionMapper = definitionMapper;
        this.instanceMapper = instanceMapper;
    }

    @Override
    public MxzSignalAcceptResult accept(MxzSignalAcceptCommand cmd) {
        var existing = signalMapper.selectBySourceKey(cmd.tenantId(), cmd.providerKey(), cmd.signalKey());
        byte[] hash = MxzSha256.digestUtf8(cmd.payloadJson());
        if (existing != null) {
            if (!Arrays.equals(existing.getPayloadHash(), hash)) {
                throw new MxzApplicationException("IDEMPOTENCY_CONFLICT", "signal key payload mismatch");
            }
            return new MxzSignalAcceptResult(
                    existing.getSignalId(),
                    true,
                    existing.getProcessStatus(),
                    cn.net.mxz.timeimprint.task.service.storage.mysql.MxzStorageTime.toInstant(
                            existing.getReceivedAt()));
        }
        var def = definitionMapper.selectById(cmd.definitionId());
        if (def == null || !cmd.tenantId().equals(def.getTenantId())) {
            throw new MxzApplicationException("RESOURCE_NOT_FOUND", "definition not found");
        }
        Long triggerBindingId = null;
        if (cmd.instanceId() != null) {
            var inst = instanceMapper.selectById(cmd.instanceId());
            if (inst == null || inst.getDefinitionId() != cmd.definitionId()) {
                throw new MxzApplicationException("RESOURCE_NOT_FOUND", "instance not found");
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
        row.setOccurredAt(MxzStorageTime.toUtcLdt(cmd.occurredAt()));
        row.setReceivedAt(MxzStorageTime.toUtcLdt(cmd.now()));
        row.setPayloadJson(cmd.payloadJson());
        row.setPayloadHash(hash);
        row.setProcessStatus("READY");
        row.setAttemptCount(0);
        row.setMaxAttempts(8);
        row.setNextAttemptAt(MxzStorageTime.toUtcLdt(cmd.now()));
        row.setCreatedAt(MxzStorageTime.toUtcLdt(cmd.now()));
        row.setUpdatedAt(MxzStorageTime.toUtcLdt(cmd.now()));
        signalMapper.insert(row);
        return new MxzSignalAcceptResult(row.getSignalId(), false, "READY", cmd.now());
    }
}
