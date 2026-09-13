package cn.net.mxz.timeimprint.task.service.storage.mysql.mapper;

import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskSignalRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskSignalMapper {

    void insert(TaskSignalRow row);

    TaskSignalRow selectById(@Param("signalId") long signalId);

    TaskSignalRow selectByIdForUpdate(@Param("signalId") long signalId);

    TaskSignalRow selectBySourceKey(@Param("tenantId") String tenantId,
                                    @Param("providerKey") String providerKey,
                                    @Param("signalKey") String signalKey);

    /** Worker claim: SKIP LOCKED batch. */
    List<TaskSignalRow> claimReadySignals(@Param("now") LocalDateTime now,
                                          @Param("limit") int limit,
                                          @Param("leaseOwner") String leaseOwner,
                                          @Param("leaseUntil") LocalDateTime leaseUntil,
                                          @Param("executionToken") String executionToken,
                                          @Param("claimedAt") LocalDateTime claimedAt);

    int claimSignal(@Param("signalId") long signalId,
                    @Param("leaseOwner") String leaseOwner,
                    @Param("leaseUntil") LocalDateTime leaseUntil,
                    @Param("executionToken") String executionToken,
                    @Param("updatedAt") LocalDateTime updatedAt);

    int completeSignal(@Param("signalId") long signalId,
                       @Param("executionToken") String executionToken,
                       @Param("processStatus") String processStatus,
                       @Param("resultCode") String resultCode,
                       @Param("resultSummary") String resultSummary,
                       @Param("processedAt") LocalDateTime processedAt,
                       @Param("updatedAt") LocalDateTime updatedAt);

    /** Ignore signals by definition/binding (pause/retire). */
    int ignoreByBindingGeneration(@Param("definitionId") long definitionId,
                                  @Param("triggerBindingId") long triggerBindingId,
                                  @Param("scheduleGeneration") long scheduleGeneration,
                                  @Param("resultCode") String resultCode,
                                  @Param("processedAt") LocalDateTime processedAt,
                                  @Param("updatedAt") LocalDateTime updatedAt);

    /** Return IDs of READY/RETRY_WAIT signals due for processing. */
    List<Long> selectReadyDueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    List<Long> selectExpiredRunningIds(@Param("limit") int limit);

    int recoverToRetryWait(@Param("signalId") long signalId,
                           @Param("executionToken") String executionToken,
                           @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                           @Param("updatedAt") LocalDateTime updatedAt);

    int recoverToDead(@Param("signalId") long signalId,
                      @Param("executionToken") String executionToken,
                      @Param("resultCode") String resultCode,
                      @Param("resultSummary") String resultSummary,
                      @Param("processedAt") LocalDateTime processedAt,
                      @Param("updatedAt") LocalDateTime updatedAt);

    int countByParent(@Param("parentSignalId") long parentSignalId);
}
