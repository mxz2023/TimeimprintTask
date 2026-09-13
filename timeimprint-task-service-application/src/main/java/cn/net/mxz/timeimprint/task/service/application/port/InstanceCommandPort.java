package cn.net.mxz.timeimprint.task.service.application.port;

import java.time.Instant;

/**
 * Port for instance-level command side-effects:
 * cancelling remaining READY/RETRY_WAIT actions when an instance reaches TERMINAL.
 */
public interface InstanceCommandPort {

    /** Cancel all READY/RETRY_WAIT action jobs for the given instance. */
    void cancelRemainingActions(long instanceId, Instant now);

    /**
     * Cancel READY/RETRY_WAIT actions for the instance except those tied to {@code keepTransitionId}
     * (terminal signal path keeps in-flight transition actions while dropping stale decoys).
     */
    void cancelRemainingActionsExceptTransition(long instanceId, long keepTransitionId, Instant now);
}
