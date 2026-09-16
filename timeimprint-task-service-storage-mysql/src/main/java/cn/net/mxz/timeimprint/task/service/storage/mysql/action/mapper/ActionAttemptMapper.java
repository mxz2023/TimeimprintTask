package cn.net.mxz.timeimprint.task.service.storage.mysql.action.mapper;

import cn.net.mxz.timeimprint.task.service.storage.mysql.action.row.ActionAttemptRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActionAttemptMapper {

    void insert(ActionAttemptRow row);

    List<ActionAttemptRow> selectByActionJobId(@Param("actionJobId") long actionJobId);

    int completeAttempt(@Param("actionJobId") long actionJobId,
                        @Param("executionToken") String executionToken,
                        @Param("outcome") String outcome,
                        @Param("errorClass") String errorClass,
                        @Param("errorCode") String errorCode,
                        @Param("safeSummary") String safeSummary,
                        @Param("finishedAt") LocalDateTime finishedAt);

    int markEffectStarted(@Param("actionJobId") long actionJobId,
                          @Param("executionToken") String executionToken,
                          @Param("effectStartedAt") LocalDateTime effectStartedAt);

    Integer selectMaxAttemptNo(@Param("actionJobId") long actionJobId);
}
