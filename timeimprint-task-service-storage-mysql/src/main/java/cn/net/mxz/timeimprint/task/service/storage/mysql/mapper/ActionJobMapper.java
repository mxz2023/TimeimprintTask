package cn.net.mxz.timeimprint.task.service.storage.mysql.mapper;

import cn.net.mxz.timeimprint.task.service.storage.mysql.row.ActionJobRow;
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

    /** Scan READY action IDs due for execution (no lock, for worker polling). */
    List<Long> selectReadyDueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** Cancel READY/RETRY_WAIT actions for a given instance/controlGeneration. */
    int cancelByControlGeneration(@Param("instanceId") long instanceId,
                                  @Param("definitionControlGeneration") long definitionControlGeneration,
                                  @Param("outcomeCode") String outcomeCode,
                                  @Param("completedAt") LocalDateTime completedAt,
                                  @Param("updatedAt") LocalDateTime updatedAt);

    /** Cancel all READY/RETRY_WAIT actions for a given instance (e.g. complete/skip). */
    int cancelReadyByInstance(@Param("instanceId") long instanceId,
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
