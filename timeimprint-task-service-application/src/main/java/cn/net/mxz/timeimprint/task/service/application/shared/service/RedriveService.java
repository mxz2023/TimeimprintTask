package cn.net.mxz.timeimprint.task.service.application.shared.service;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.common.hashing.Sha256;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.action.model.ActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.signal.model.SignalRecord;
import cn.net.mxz.timeimprint.task.service.application.action.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.shared.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.definition.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.signal.port.TaskSignalRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import org.springframework.stereotype.Service;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;

/** I06/I07 redrive for DEAD Signal / LOCAL_TRANSACTIONAL Action. */
@Service
public class RedriveService {

    private static final int MAX_REDRIVES = 3;

    private final ActorContextProvider actorContextProvider;
    private final TaskSignalRepository signalRepository;
    private final ActionJobExecutionPort actionJobExecutionPort;
    private final TaskDefinitionRepository definitionRepository;
    private final CommandDedupRepository commandDedupRepository;
    private final TransactionBoundary tx;
    private final BusinessClock clock;

    public RedriveService(
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

    public SignalRecord redriveSignal(long signalId, String requestId, String expectedStatus, String reason) {
        var actor = actorContextProvider.requireCurrentActor();
        if (!"DEAD".equals(expectedStatus)) {
            throw new ApplicationException("INVALID_REQUEST", "expectedStatus must be DEAD");
        }
        String op = "POST /internal/v1/task-signals/" + signalId + "/commands/redrive";
        byte[] hash = Sha256.digestUtf8(requestId + ":DEAD:" + reason);
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
                throw new ApplicationException("RETRY_LATER", "dedup in progress");
            }
            var source = signalRepository
                    .findByIdForUpdate(signalId)
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "signal"));
            if (!"DEAD".equals(source.processStatus())) {
                throw new ApplicationException("STATE_CONFLICT", "signal not DEAD");
            }
            long rootId = resolveSignalRoot(source);
            int existing = signalRepository.countRedrives(rootId);
            if (existing >= MAX_REDRIVES) {
                throw new ApplicationException("STATE_CONFLICT", "max redrive exceeded");
            }
            var def = definitionRepository
                    .findById(source.definitionId())
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "definition"));
            if (def.controlGeneration() != source.definitionControlGeneration()) {
                throw new ApplicationException("STATE_CONFLICT", "controlGeneration mismatch");
            }
            if (def.controlState() == cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState.RETIRED
                    || def.controlState() == cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState.PAUSED) {
                throw new ApplicationException("STATE_CONFLICT", "definition not active");
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

    public ActionJobRecord redriveAction(long actionJobId, String requestId, String expectedStatus, String reason) {
        var actor = actorContextProvider.requireCurrentActor();
        if (!"DEAD".equals(expectedStatus)) {
            throw new ApplicationException("INVALID_REQUEST", "expectedStatus must be DEAD");
        }
        String op = "POST /internal/v1/action-jobs/" + actionJobId + "/commands/redrive";
        byte[] hash = Sha256.digestUtf8(requestId + ":DEAD:" + reason);
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
                throw new ApplicationException("RETRY_LATER", "dedup in progress");
            }
            var source = actionJobExecutionPort
                    .findByIdForUpdate(actionJobId)
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "actionJob"));
            if (!"DEAD".equals(source.status())) {
                throw new ApplicationException("STATE_CONFLICT", "action not DEAD");
            }
            if (!"LOCAL_TRANSACTIONAL".equals(source.executionMode())) {
                throw new ApplicationException("STATE_CONFLICT", "only LOCAL_TRANSACTIONAL redrive allowed");
            }
            long rootId = resolveActionRoot(source);
            int existing = actionJobExecutionPort.countRedrives(rootId);
            if (existing >= MAX_REDRIVES) {
                throw new ApplicationException("STATE_CONFLICT", "max redrive exceeded");
            }
            var def = definitionRepository
                    .findById(source.definitionId())
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "definition"));
            if (def.controlGeneration() != source.definitionControlGeneration()) {
                throw new ApplicationException("STATE_CONFLICT", "controlGeneration mismatch");
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

    private long resolveSignalRoot(SignalRecord signal) {
        SignalRecord cur = signal;
        while (cur.parentSignalId() != null) {
            cur = signalRepository
                    .findById(cur.parentSignalId())
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "parent signal"));
        }
        return cur.signalId();
    }

    private long resolveActionRoot(ActionJobRecord action) {
        ActionJobRecord cur = action;
        while (cur.parentActionJobId() != null) {
            cur = actionJobExecutionPort
                    .findById(cur.parentActionJobId())
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "parent action"));
        }
        return cur.actionJobId();
    }
}
