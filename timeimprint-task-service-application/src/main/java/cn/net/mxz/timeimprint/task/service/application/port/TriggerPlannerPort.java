package cn.net.mxz.timeimprint.task.service.application.port;

import java.time.Instant;

/**
 * Port for the background Trigger Planner.
 * Implementation in service-storage-mysql scans due calendar bindings
 * and extends 7-day windows per binding.
 */
public interface TriggerPlannerPort {

    /**
     * Find up to {@code bindingBatch} due trigger bindings and for each extend the
     * 7-day occurrence window up to {@code maxPerBinding} new occurrences.
     *
     * @param now            current business time
     * @param bindingBatch   max trigger bindings to process in this round
     * @param maxPerBinding  max new occurrences per binding
     */
    void planDueBindings(Instant now, int bindingBatch, int maxPerBinding);

    /**
     * Plan a single binding in its own short transaction (definition → binding lock order).
     * Used by concurrent planning tests and by {@link #planDueBindings}.
     */
    void planBinding(long triggerBindingId, Instant now, int maxPerBinding);
}
