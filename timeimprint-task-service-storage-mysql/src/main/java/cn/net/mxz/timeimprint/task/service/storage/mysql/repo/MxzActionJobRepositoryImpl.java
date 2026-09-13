package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.model.MxzAttemptRecord;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobRepository;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzRowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzStorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.ActionAttemptMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.ActionJobMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.ActionAttemptRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.ActionJobRow;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MxzActionJobRepositoryImpl implements ActionJobRepository, ActionJobExecutionPort {

    private final ActionJobMapper mapper;
    private final ActionAttemptMapper attemptMapper;

    public MxzActionJobRepositoryImpl(ActionJobMapper mapper, ActionAttemptMapper attemptMapper) {
        this.mapper = mapper;
        this.attemptMapper = attemptMapper;
    }

    @Override
    public Optional<Long> findIdForUpdate(long actionJobId) {
        var row = mapper.selectByIdForUpdate(actionJobId);
        return row == null ? Optional.empty() : Optional.of(row.getActionJobId());
    }

    @Override
    public List<MxzActionJobRecord> listByInstance(long instanceId) {
        return mapper.selectByInstanceId(instanceId).stream().map(MxzRowMapper::toAction).toList();
    }

    @Override
    public Optional<MxzActionJobRecord> findByIdForUpdate(long actionJobId) {
        return Optional.ofNullable(MxzRowMapper.toAction(mapper.selectByIdForUpdate(actionJobId)));
    }

    @Override
    public Optional<MxzActionJobRecord> findById(long actionJobId) {
        return Optional.ofNullable(MxzRowMapper.toAction(mapper.selectById(actionJobId)));
    }

    @Override
    public List<Long> listReadyDueIds(Instant now, int limit) {
        return mapper.selectReadyDueIds(MxzStorageTime.toUtcLdt(now), limit);
    }

    @Override
    public List<Long> listReadyDueIdsNewestFirst(Instant now, int limit) {
        return mapper.selectReadyDueIdsNewestFirst(MxzStorageTime.toUtcLdt(now), limit);
    }

    @Override
    public List<MxzActionJobRecord> listFiltered(Long definitionId, Long instanceId,
            String status, String handlerKey, int limit, String cursor) {
        Long cursorId = parseCursor(cursor);
        return mapper.selectList(definitionId, instanceId, status, handlerKey, limit, cursorId)
                .stream().map(MxzRowMapper::toAction).toList();
    }

    @Override
    public List<MxzAttemptRecord> listAttempts(long actionJobId) {
        return attemptMapper.selectByActionJobId(actionJobId).stream()
                .map(r -> new MxzAttemptRecord(
                        r.getAttemptNo() == null ? 0 : r.getAttemptNo(),
                        MxzStorageTime.toInstant(r.getStartedAt()),
                        MxzStorageTime.toInstant(r.getFinishedAt()),
                        MxzStorageTime.toInstant(r.getEffectStartedAt()),
                        r.getOutcome(),
                        r.getErrorClass(),
                        r.getErrorCode(),
                        r.getProviderReference(),
                        r.getSafeSummary()))
                .toList();
    }

    @Override
    public int countRedrives(long rootActionJobId) {
        return mapper.countByParent(rootActionJobId);
    }

    @Override
    public long insertRedrive(MxzActionJobRecord template, long rootActionJobId, int redriveNo, Instant now) {
        var src = mapper.selectById(template.actionJobId());
        if (src == null) {
            throw new MxzApplicationException("RESOURCE_NOT_FOUND", "actionJob");
        }
        ActionJobRow row = new ActionJobRow();
        row.setTenantId(src.getTenantId());
        row.setDefinitionId(src.getDefinitionId());
        row.setInstanceId(src.getInstanceId());
        row.setTransitionId(src.getTransitionId());
        row.setDefinitionControlGeneration(src.getDefinitionControlGeneration());
        row.setParentActionJobId(rootActionJobId);
        row.setRedriveNo(redriveNo);
        row.setHandlerKey(src.getHandlerKey());
        row.setActionKey(src.getActionKey() + ":redrive:" + redriveNo);
        row.setExecutionMode(src.getExecutionMode());
        row.setSchemaVersion(src.getSchemaVersion());
        row.setTargetType(src.getTargetType());
        row.setTargetId(src.getTargetId());
        row.setPayloadJson(src.getPayloadJson());
        row.setPayloadHash(src.getPayloadHash());
        row.setAvailableAt(MxzStorageTime.toUtcLdt(now));
        row.setExpiresAt(src.getExpiresAt());
        row.setStatus("READY");
        row.setAttemptCount(0);
        row.setMaxAttempts(src.getMaxAttempts());
        row.setNextAttemptAt(MxzStorageTime.toUtcLdt(now));
        row.setCreatedAt(MxzStorageTime.toUtcLdt(now));
        row.setUpdatedAt(MxzStorageTime.toUtcLdt(now));
        mapper.insert(row);
        return row.getActionJobId();
    }

    @Override
    public void markSucceeded(long actionJobId, String outcomeCode, String summary, Instant completedAt) {
        var row = mapper.selectByIdForUpdate(actionJobId);
        if (row == null) {
            throw new MxzApplicationException("RESOURCE_NOT_FOUND", "actionJob");
        }
        String token = row.getExecutionToken();
        if (token == null) {
            token = UUID.randomUUID().toString();
            int claimed = mapper.claimAction(
                    actionJobId,
                    "local-tx",
                    MxzStorageTime.toUtcLdt(completedAt).plusMinutes(1),
                    token,
                    MxzStorageTime.toUtcLdt(completedAt));
            if (claimed != 1) {
                throw new MxzApplicationException("STATE_CONFLICT", "action claim failed");
            }
        }
        int closed = mapper.completeAction(
                actionJobId,
                token,
                "SUCCEEDED",
                outcomeCode,
                summary,
                MxzStorageTime.toUtcLdt(completedAt),
                MxzStorageTime.toUtcLdt(completedAt));
        if (closed != 1) {
            throw new MxzApplicationException("STATE_CONFLICT", "action CAS failed");
        }
    }

    @Override
    public void markCancelled(long actionJobId, String outcomeCode, Instant completedAt) {
        LocalDateTime at = MxzStorageTime.toUtcLdt(completedAt);
        mapper.cancelReadyById(actionJobId, outcomeCode, at, at);
    }

    @Override
    public Optional<String> claimForExecution(
            long actionJobId, String leaseOwner, Instant leaseUntil, Instant now) {
        String token = UUID.randomUUID().toString();
        LocalDateTime nowLdt = MxzStorageTime.toUtcLdt(now);
        int claimed = mapper.claimAction(
                actionJobId, leaseOwner, MxzStorageTime.toUtcLdt(leaseUntil), token, nowLdt);
        if (claimed != 1) {
            return Optional.empty();
        }
        Integer max = attemptMapper.selectMaxAttemptNo(actionJobId);
        int attemptNo = (max == null ? 0 : max) + 1;
        ActionAttemptRow attempt = new ActionAttemptRow();
        attempt.setActionJobId(actionJobId);
        attempt.setAttemptNo(attemptNo);
        attempt.setExecutionToken(token);
        attempt.setStartedAt(nowLdt);
        attemptMapper.insert(attempt);
        return Optional.of(token);
    }

    @Override
    public boolean markEffectStarted(long actionJobId, String executionToken, Instant now) {
        return attemptMapper.markEffectStarted(
                        actionJobId, executionToken, MxzStorageTime.toUtcLdt(now))
                == 1;
    }

    @Override
    public void cancelRunning(
            long actionJobId, String executionToken, String outcomeCode, Instant completedAt) {
        LocalDateTime at = MxzStorageTime.toUtcLdt(completedAt);
        mapper.cancelRunning(actionJobId, executionToken, outcomeCode, at, at);
        attemptMapper.completeAttempt(
                actionJobId, executionToken, "CONTROL_BARRIER", null, outcomeCode, outcomeCode, at);
    }

    @Override
    public boolean completeWithToken(
            long actionJobId,
            String executionToken,
            String status,
            String outcomeCode,
            String summary,
            Instant completedAt) {
        LocalDateTime at = MxzStorageTime.toUtcLdt(completedAt);
        return mapper.completeAction(actionJobId, executionToken, status, outcomeCode, summary, at, at) == 1;
    }

    @Override
    public void completeAttempt(
            long actionJobId,
            String executionToken,
            String outcome,
            String errorClass,
            String errorCode,
            String summary,
            Instant finishedAt) {
        attemptMapper.completeAttempt(
                actionJobId,
                executionToken,
                outcome,
                errorClass,
                errorCode,
                summary,
                MxzStorageTime.toUtcLdt(finishedAt));
    }

    @Override
    public List<Long> listExpiredRunningIds(int limit) {
        return mapper.selectExpiredRunningIds(limit);
    }

    @Override
    public boolean recoverExpiredLease(long actionJobId, Instant now) {
        var row = mapper.selectByIdForUpdate(actionJobId);
        if (row == null || !"RUNNING".equals(row.getStatus()) || row.getExecutionToken() == null) {
            return false;
        }
        String token = row.getExecutionToken();
        LocalDateTime nowLdt = MxzStorageTime.toUtcLdt(now);

        var attempts = attemptMapper.selectByActionJobId(actionJobId);
        ActionAttemptRow open = null;
        for (ActionAttemptRow a : attempts) {
            if (token.equals(a.getExecutionToken()) && a.getFinishedAt() == null) {
                open = a;
                break;
            }
        }
        boolean effectStarted = open != null && open.getEffectStartedAt() != null;
        boolean external = "EXTERNAL".equals(row.getExecutionMode());

        if (external && effectStarted) {
            attemptMapper.completeAttempt(
                    actionJobId, token, "UNKNOWN", "LEASE", "LEASE_EXPIRED",
                    "EXTERNAL lease expired after effectStartedAt", nowLdt);
            return mapper.recoverToTerminal(
                            actionJobId,
                            token,
                            "UNKNOWN",
                            "LEASE_EXPIRED",
                            "EXTERNAL lease expired after effectStartedAt",
                            nowLdt,
                            nowLdt)
                    == 1;
        }

        attemptMapper.completeAttempt(
                actionJobId, token, "RETRYABLE_FAILURE", "LEASE", "LEASE_EXPIRED",
                "lease expired before confirmed effect", nowLdt);
        int attemptCount = row.getAttemptCount() == null ? 0 : row.getAttemptCount();
        int maxAttempts = row.getMaxAttempts() == null ? 5 : row.getMaxAttempts();
        if (attemptCount >= maxAttempts) {
            return mapper.recoverToTerminal(
                            actionJobId,
                            token,
                            "DEAD",
                            "LEASE_EXPIRED",
                            "attempts exhausted after lease expiry",
                            nowLdt,
                            nowLdt)
                    == 1;
        }
        Instant next = now.plusSeconds(backoffSeconds(attemptCount));
        return mapper.recoverToRetryWait(
                        actionJobId, token, MxzStorageTime.toUtcLdt(next), nowLdt)
                == 1;
    }

    @Override
    public boolean expireIfDue(long actionJobId, Instant now) {
        LocalDateTime nowLdt = MxzStorageTime.toUtcLdt(now);
        return mapper.markExpiredIfDue(actionJobId, "EXPIRED", nowLdt, nowLdt, nowLdt) == 1;
    }

    @Override
    public void completeRetryableFailure(
            long actionJobId, String executionToken, String outcomeCode, String summary, Instant now) {
        var row = mapper.selectByIdForUpdate(actionJobId);
        if (row == null) {
            throw new MxzApplicationException("RESOURCE_NOT_FOUND", "actionJob");
        }
        LocalDateTime nowLdt = MxzStorageTime.toUtcLdt(now);
        attemptMapper.completeAttempt(
                actionJobId, executionToken, "RETRYABLE_FAILURE", null, outcomeCode, summary, nowLdt);
        int attemptCount = row.getAttemptCount() == null ? 0 : row.getAttemptCount();
        int maxAttempts = row.getMaxAttempts() == null ? 5 : row.getMaxAttempts();
        if (attemptCount >= maxAttempts) {
            int closed = mapper.completeAction(
                    actionJobId, executionToken, "DEAD", outcomeCode, summary, nowLdt, nowLdt);
            if (closed != 1) {
                throw new MxzApplicationException("STATE_CONFLICT", "action CAS failed");
            }
            return;
        }
        Instant next = now.plusSeconds(backoffSeconds(attemptCount));
        int closed = mapper.completeToRetryWait(
                actionJobId,
                executionToken,
                outcomeCode,
                summary,
                MxzStorageTime.toUtcLdt(next),
                nowLdt);
        if (closed != 1) {
            throw new MxzApplicationException("STATE_CONFLICT", "action CAS failed");
        }
    }

    @Override
    public void releasePolicyBlocked(
            long actionJobId, String executionToken, String outcomeCode, Instant now) {
        LocalDateTime nowLdt = MxzStorageTime.toUtcLdt(now);
        attemptMapper.completeAttempt(
                actionJobId, executionToken, "POLICY_BLOCKED", "POLICY", outcomeCode, outcomeCode, nowLdt);
        int released = mapper.refundPolicyBlocked(actionJobId, executionToken, outcomeCode, nowLdt);
        if (released != 1) {
            throw new MxzApplicationException("STATE_CONFLICT", "policy refund CAS failed");
        }
    }

    /** Technical backoff after attempt N failure: 5/30/120/600 seconds. */
    static long backoffSeconds(int attemptCount) {
        int[] delays = {5, 30, 120, 600};
        int idx = Math.min(Math.max(attemptCount, 1), delays.length) - 1;
        return delays[idx];
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new MxzApplicationException("INVALID_CURSOR", "cursor");
        }
    }
}
