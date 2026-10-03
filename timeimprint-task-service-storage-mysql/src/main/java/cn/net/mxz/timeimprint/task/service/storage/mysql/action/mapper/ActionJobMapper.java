package cn.net.mxz.timeimprint.task.service.storage.mysql.action.mapper;

import cn.net.mxz.timeimprint.task.service.storage.mysql.action.row.ActionJobRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActionJobMapper {

    void insert(ActionJobRow row);

    ActionJobRow selectById(@Param("actionJobId") long actionJobId);

    ActionJobRow selectByIdForUpdate(@Param("actionJobId") long actionJobId);

    List<ActionJobRow> selectByInstanceId(@Param("instanceId") long instanceId);

    /** Worker claim: SKIP LOCKED. Returns updated rows. */
    List<ActionJobRow> claimReadyActions(@Param("now") LocalDateTime now,
                                          @Param("limit") int limit,
                                          @Param("leaseOwner") String leaseOwner,
                                          @Param("leaseUntil") LocalDateTime leaseUntil,
                                          @Param("executionToken") String executionToken,
                                          @Param("claimedAt") LocalDateTime claimedAt);

    /** List RUNNING actions whose lease expired (DB UTC), SKIP LOCKED. */
    List<Long> selectExpiredRunningIds(@Param("limit") int limit);

    /** Transition expired RUNNING → RETRY_WAIT (clear lease/token). */
    int recoverToRetryWait(@Param("actionJobId") long actionJobId,
                           @Param("executionToken") String executionToken,
                           @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                           @Param("updatedAt") LocalDateTime updatedAt);

    /** Transition expired RUNNING → UNKNOWN/DEAD (clear lease/token). */
    int recoverToTerminal(@Param("actionJobId") long actionJobId,
                          @Param("executionToken") String executionToken,
                          @Param("status") String status,
                          @Param("outcomeCode") String outcomeCode,
                          @Param("outcomeSummary") String outcomeSummary,
                          @Param("completedAt") LocalDateTime completedAt,
                          @Param("updatedAt") LocalDateTime updatedAt);

    int claimAction(@Param("actionJobId") long actionJobId,
                    @Param("leaseOwner") String leaseOwner,
                    @Param("leaseUntil") LocalDateTime leaseUntil,
                    @Param("executionToken") String executionToken,
                    @Param("updatedAt") LocalDateTime updatedAt);

    /** Cancel RUNNING under a specific executionToken (barrier before EXTERNAL call). */
    int cancelRunning(@Param("actionJobId") long actionJobId,
                      @Param("executionToken") String executionToken,
                      @Param("outcomeCode") String outcomeCode,
                      @Param("completedAt") LocalDateTime completedAt,
                      @Param("updatedAt") LocalDateTime updatedAt);

    int completeAction(@Param("actionJobId") long actionJobId,
                       @Param("executionToken") String executionToken,
                       @Param("status") String status,
                       @Param("outcomeCode") String outcomeCode,
                       @Param("outcomeSummary") String outcomeSummary,
                       @Param("completedAt") LocalDateTime completedAt,
                       @Param("updatedAt") LocalDateTime updatedAt);

    /** RUNNING → RETRY_WAIT with next_attempt_at (technical retry / policy refund path uses separate method). */
    int completeToRetryWait(@Param("actionJobId") long actionJobId,
                            @Param("executionToken") String executionToken,
                            @Param("outcomeCode") String outcomeCode,
                            @Param("outcomeSummary") String outcomeSummary,
                            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                            @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Policy barrier before side-effect: RUNNING → READY, refund attempt_count, clear lease/token.
     */
    int refundPolicyBlocked(@Param("actionJobId") long actionJobId,
                            @Param("executionToken") String executionToken,
                            @Param("outcomeCode") String outcomeCode,
                            @Param("updatedAt") LocalDateTime updatedAt);

    /** READY/RETRY_WAIT → EXPIRED when expiresAt reached before claim. */
    int markExpiredIfDue(@Param("actionJobId") long actionJobId,
                         @Param("outcomeCode") String outcomeCode,
                         @Param("completedAt") LocalDateTime completedAt,
                         @Param("updatedAt") LocalDateTime updatedAt,
                         @Param("now") LocalDateTime now);

    /** Scan READY action IDs due for execution (no lock, for worker polling). */
    List<Long> selectReadyDueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** Fairness share: newest available_at among due READY/RETRY_WAIT. */
    List<Long> selectReadyDueIdsNewestFirst(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** Cancel READY/RETRY_WAIT actions for a given instance/controlGeneration. */
    int cancelByControlGeneration(@Param("instanceId") long instanceId,
                                  @Param("definitionControlGeneration") long definitionControlGeneration,
                                  @Param("outcomeCode") String outcomeCode,
                                  @Param("completedAt") LocalDateTime completedAt,
                                  @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * READY/RETRY_WAIT action ids for one instance, smallest primary key first.
     * Callers lock each id with {@link #selectByIdForUpdate} before updating.
     */
    List<Long> selectReadyIdsByInstance(@Param("instanceId") long instanceId);

    /** Same as {@link #selectReadyIdsByInstance}, excluding one transition. */
    List<Long> selectReadyIdsByInstanceExceptTransition(@Param("instanceId") long instanceId,
                                                        @Param("keepTransitionId") long keepTransitionId);

    /** Cancel all READY/RETRY_WAIT actions for a given instance (e.g. complete/skip). */
    int cancelReadyByInstance(@Param("instanceId") long instanceId,
                              @Param("outcomeCode") String outcomeCode,
                              @Param("completedAt") LocalDateTime completedAt,
                              @Param("updatedAt") LocalDateTime updatedAt);

    /** Cancel READY/RETRY_WAIT except actions belonging to keepTransitionId (A26). */
    int cancelReadyByInstanceExceptTransition(@Param("instanceId") long instanceId,
                                              @Param("keepTransitionId") long keepTransitionId,
                                              @Param("outcomeCode") String outcomeCode,
                                              @Param("completedAt") LocalDateTime completedAt,
                                              @Param("updatedAt") LocalDateTime updatedAt);

    /** Cancel unstarted actions for a definition (lazy Worker / cleanup batch). */
    int cancelReadyByDefinition(@Param("definitionId") long definitionId,
                                @Param("outcomeCode") String outcomeCode,
                                @Param("completedAt") LocalDateTime completedAt,
                                @Param("updatedAt") LocalDateTime updatedAt);

    /** Cancel one READY/RETRY_WAIT action (controlGeneration / control-state barrier). */
    int cancelReadyById(@Param("actionJobId") long actionJobId,
                        @Param("outcomeCode") String outcomeCode,
                        @Param("completedAt") LocalDateTime completedAt,
                        @Param("updatedAt") LocalDateTime updatedAt);

    /** List actions with optional filters for diagnostic query (I03). */
    List<ActionJobRow> selectList(@Param("definitionId") Long definitionId,
                                  @Param("instanceId") Long instanceId,
                                  @Param("status") String status,
                                  @Param("handlerKey") String handlerKey,
                                  @Param("limit") int limit,
                                  @Param("cursor") Long cursor);

    int countByParent(@Param("parentActionJobId") long parentActionJobId);
}
