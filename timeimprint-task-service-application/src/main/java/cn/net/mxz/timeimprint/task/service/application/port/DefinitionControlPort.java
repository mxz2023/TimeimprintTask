package cn.net.mxz.timeimprint.task.service.application.port;

import java.time.Instant;

/**
 * Port for definition control side-effects: cancel WAITING instances
 * and future READY Signals when pausing or retiring a definition.
 */
public interface DefinitionControlPort {

    /**
     * Cancel all WAITING instances and their planned signals for all trigger bindings
     * of the given definition. Called atomically within the pause/retire transaction.
     */
    void cancelWindowAndSignals(long definitionId, Instant now);
}
