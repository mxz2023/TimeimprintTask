package cn.net.mxz.timeimprint.task.service.application.limit;

/** Platform scale and timeout constants from docs/03-INTEGRATION-CONTRACTS.md §3. */
public final class PlatformLimits {

    public static final int MAX_PARTICIPANTS_PER_SCOPE = 50;
    public static final int MAX_RECIPIENTS = 10;
    public static final int MAX_TRIGGER_BINDINGS = 8;
    public static final int MAX_OCCURRENCES_PER_WRITE_TX = 100;
    public static final int MAX_ACTIONS_PER_TRANSITION = 100;
    public static final int MAX_ACTIONS_PER_WRITE_TX = 500;
    public static final int MAX_SCENARIO_MUTATIONS = 32;
    public static final int MAX_SCENARIO_MUTATION_BYTES = 65536;
    public static final int MAX_JSON_VALUE_BYTES = 65536;
    public static final int MAX_TRANSITION_PLAN_BYTES = 1048576;
    public static final int MAX_MUTATED_ROWS_PER_WRITE_TX = 2000;
    public static final int BUSINESS_TX_TIMEOUT_SECONDS = 5;

    /** {@code LEASE_SECONDS - ACTION_LEASE_SAFETY_SECONDS} default (30 − 5). */
    public static final int ACTION_LEASE_SAFETY_SECONDS = 5;

    private PlatformLimits() {}
}
