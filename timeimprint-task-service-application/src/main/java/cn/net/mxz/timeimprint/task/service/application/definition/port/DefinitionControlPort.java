package cn.net.mxz.timeimprint.task.service.application.definition.port;

import java.time.Instant;

/**
 * Port for definition control side-effects: cancel WAITING instances
 * and future READY Signals when pausing or retiring a definition,
 * and rebuild the future window on resume.
 */
public interface DefinitionControlPort {

    /**
     * Cancel all WAITING instances and their planned signals for all trigger bindings
     * of the given definition. Called atomically within the pause/retire transaction.
     */
    void cancelWindowAndSignals(long definitionId, Instant now);

    /**
     * After resume to ACTIVE, materialize the next 7-day WAITING window from {@code now}
     * (exclusive after). Does not backfill occurrences that fell during the pause interval.
     * Uses the definition's current controlGeneration and binding scheduleGeneration.
     */
    void rebuildFutureWindow(long definitionId, Instant now);
}
