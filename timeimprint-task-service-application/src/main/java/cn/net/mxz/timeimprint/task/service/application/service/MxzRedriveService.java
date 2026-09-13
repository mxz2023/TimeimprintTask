package cn.net.mxz.timeimprint.task.service.application.service;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.common.MxzSha256;
import cn.net.mxz.timeimprint.task.service.application.actor.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.model.MxzSignalRecord;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TaskSignalRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import org.springframework.stereotype.Service;

/** I06/I07 redrive for DEAD Signal / LOCAL_TRANSACTIONAL Action. */
@Service
public class MxzRedriveService {

    private static final int MAX_REDRIVES = 3;

    private final ActorContextProvider actorContextProvider;
    private final TaskSignalRepository signalRepository;
    private final ActionJobExecutionPort actionJobExecutionPort;
    private final TaskDefinitionRepository definitionRepository;
    private final CommandDedupRepository commandDedupRepository;
    private final TransactionBoundary tx;
    private final BusinessClock clock;

    public MxzRedriveService(
            ActorContextProvider actorContextProvider,
            TaskSignalRepository signalRepository,
            ActionJobExecutionPort actionJobExecutionPort,
            TaskDefinitionRepository definitionRepository,
            CommandDedupRepository commandDedupRepository,
            TransactionBoundary tx,
            BusinessClock clock) {
        this.actorContextProvider = actorContextProvider;
        this.signalRepository = signalRepository;
        this.actionJobExecutionPort = actionJobExecutionPort;
        this.definitionRepository = definitionRepository;
        this.commandDedupRepository = commandDedupRepository;
        this.tx = tx;
        this.clock = clock;
    }

    public MxzSignalRecord redriveSignal(long signalId, String requestId, String expectedStatus, String reason) {
        var actor = actorContextProvider.requireCurrentActor();
        if (!"DEAD".equals(expectedStatus)) {
            throw new MxzApplicationException("INVALID_REQUEST", "expectedStatus must be DEAD");
        }
        String op = "POST /internal/v1/task-signals/" + signalId + "/commands/redrive";
        byte[] hash = MxzSha256.digestUtf8(requestId + ":DEAD:" + reason);
        return tx.execute(() -> {
            var completed = commandDedupRepository.findCompletedResponseJson(
                    actor.tenantKey(), actor.principalId(), op, requestId);
            if (completed.isPresent()) {
                long id = Long.parseLong(completed.get());
                return signalRepository.findById(id).orElseThrow();
            }
            if (!commandDedupRepository.tryBegin(actor.tenantKey(), actor.principalId(), op, requestId, hash)) {
                var again = commandDedupRepository.findCompletedResponseJson(
                        actor.tenantKey(), actor.principalId(), op, requestId);
                if (again.isPresent()) {
                    return signalRepository.findById(Long.parseLong(again.get())).orElseThrow();
                }
                throw new MxzApplicationException("RETRY_LATER", "dedup in progress");
            }
            var source = signalRepository
                    .findByIdForUpdate(signalId)
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "signal"));
            if (!"DEAD".equals(source.processStatus())) {
                throw new MxzApplicationException("STATE_CONFLICT", "signal not DEAD");
            }
            long rootId = resolveSignalRoot(source);
            int existing = signalRepository.countRedrives(rootId);
            if (existing >= MAX_REDRIVES) {
                throw new MxzApplicationException("STATE_CONFLICT", "max redrive exceeded");
            }
            var def = definitionRepository
                    .findById(source.definitionId())
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "definition"));
            if (def.controlGeneration() != source.definitionControlGeneration()) {
                throw new MxzApplicationException("STATE_CONFLICT", "controlGeneration mismatch");
            }
            if (def.controlState() == cn.net.mxz.timeimprint.task.service.kernel.domain.state.ControlState.RETIRED
                    || def.controlState() == cn.net.mxz.timeimprint.task.service.kernel.domain.state.ControlState.PAUSED) {
                throw new MxzApplicationException("STATE_CONFLICT", "definition not active");
            }
            int redriveNo = existing + 1;
            long newId = signalRepository.insertRedrive(source, rootId, redriveNo, clock.nowUtcSeconds());
            commandDedupRepository.complete(
                    actor.tenantKey(),
                    actor.principalId(),
                    op,
                    requestId,
                    "OK",
                    "SIGNAL",
                    String.valueOf(newId),
                    null,
                    String.valueOf(newId));
            return signalRepository.findById(newId).orElseThrow();
        });
    }

    public MxzActionJobRecord redriveAction(long actionJobId, String requestId, String expectedStatus, String reason) {
        var actor = actorContextProvider.requireCurrentActor();
        if (!"DEAD".equals(expectedStatus)) {
            throw new MxzApplicationException("INVALID_REQUEST", "expectedStatus must be DEAD");
        }
        String op = "POST /internal/v1/action-jobs/" + actionJobId + "/commands/redrive";
        byte[] hash = MxzSha256.digestUtf8(requestId + ":DEAD:" + reason);
        return tx.execute(() -> {
            var completed = commandDedupRepository.findCompletedResponseJson(
                    actor.tenantKey(), actor.principalId(), op, requestId);
            if (completed.isPresent()) {
                long id = Long.parseLong(completed.get());
                return actionJobExecutionPort.findById(id).orElseThrow();
            }
            if (!commandDedupRepository.tryBegin(actor.tenantKey(), actor.principalId(), op, requestId, hash)) {
                var again = commandDedupRepository.findCompletedResponseJson(
                        actor.tenantKey(), actor.principalId(), op, requestId);
                if (again.isPresent()) {
                    return actionJobExecutionPort.findById(Long.parseLong(again.get())).orElseThrow();
                }
                throw new MxzApplicationException("RETRY_LATER", "dedup in progress");
            }
            var source = actionJobExecutionPort
                    .findByIdForUpdate(actionJobId)
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "actionJob"));
            if (!"DEAD".equals(source.status())) {
                throw new MxzApplicationException("STATE_CONFLICT", "action not DEAD");
            }
            if (!"LOCAL_TRANSACTIONAL".equals(source.executionMode())) {
                throw new MxzApplicationException("STATE_CONFLICT", "only LOCAL_TRANSACTIONAL redrive allowed");
            }
            long rootId = resolveActionRoot(source);
            int existing = actionJobExecutionPort.countRedrives(rootId);
            if (existing >= MAX_REDRIVES) {
                throw new MxzApplicationException("STATE_CONFLICT", "max redrive exceeded");
            }
            var def = definitionRepository
                    .findById(source.definitionId())
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "definition"));
            if (def.controlGeneration() != source.definitionControlGeneration()) {
                throw new MxzApplicationException("STATE_CONFLICT", "controlGeneration mismatch");
            }
            int redriveNo = existing + 1;
            long newId = actionJobExecutionPort.insertRedrive(source, rootId, redriveNo, clock.nowUtcSeconds());
            commandDedupRepository.complete(
                    actor.tenantKey(),
                    actor.principalId(),
                    op,
                    requestId,
                    "OK",
                    "ACTION",
                    String.valueOf(newId),
                    null,
                    String.valueOf(newId));
            return actionJobExecutionPort.findById(newId).orElseThrow();
        });
    }

    private long resolveSignalRoot(MxzSignalRecord signal) {
        MxzSignalRecord cur = signal;
        while (cur.parentSignalId() != null) {
            cur = signalRepository
                    .findById(cur.parentSignalId())
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "parent signal"));
        }
        return cur.signalId();
    }

    private long resolveActionRoot(MxzActionJobRecord action) {
        MxzActionJobRecord cur = action;
        while (cur.parentActionJobId() != null) {
            cur = actionJobExecutionPort
                    .findById(cur.parentActionJobId())
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "parent action"));
        }
        return cur.actionJobId();
    }
}
