package cn.net.mxz.timeimprint.task.service.application.port;

import java.time.Instant;

/**
 * Port for instance-level command side-effects:
 * cancelling remaining READY/RETRY_WAIT actions when an instance reaches TERMINAL.
 */
public interface InstanceCommandPort {

    /** Cancel all READY/RETRY_WAIT action jobs for the given instance. */
    void cancelRemainingActions(long instanceId, Instant now);
}
