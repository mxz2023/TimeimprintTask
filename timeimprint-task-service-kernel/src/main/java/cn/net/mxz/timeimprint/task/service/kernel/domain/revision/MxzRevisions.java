package cn.net.mxz.timeimprint.task.service.kernel.domain.revision;

/**
 * 资源乐观锁 revision 边界与递增规则（与 Maven {@code revision} 制品版本无关）。
 */
public final class MxzRevisions {

    public static final long MIN = 1L;
    public static final long MAX = 9_007_199_254_740_991L;

    private MxzRevisions() {}

    /** 初始创建前资源尚未存在时使用 0。 */
    public static final long INITIAL_FROM = 0L;

    public static long validate(long revision) {
        if (revision < MIN || revision > MAX) {
            throw new IllegalArgumentException("revision out of range: " + revision);
        }
        return revision;
    }

    /** 迁移计划中的 fromRevision；允许 {@link #INITIAL_FROM}。 */
    public static long validateTransitionFrom(long fromRevision) {
        if (fromRevision == INITIAL_FROM) {
            return fromRevision;
        }
        return validate(fromRevision);
    }

    public static long nextAfter(long currentRevision) {
        validate(currentRevision);
        if (currentRevision >= MAX) {
            throw new IllegalArgumentException("revision overflow");
        }
        return currentRevision + 1L;
    }

    public static boolean isInitialCreation(long fromRevision, long toRevision) {
        return fromRevision == 0L && toRevision == 1L;
    }
}
