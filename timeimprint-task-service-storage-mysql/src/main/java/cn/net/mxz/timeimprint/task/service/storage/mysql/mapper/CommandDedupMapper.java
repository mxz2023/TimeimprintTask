package cn.net.mxz.timeimprint.task.service.storage.mysql.mapper;

import cn.net.mxz.timeimprint.task.service.storage.mysql.row.CommandDedupRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommandDedupMapper {

    /** Returns 1 if inserted, 0 if duplicate key (competitor). */
    int insertProcessing(CommandDedupRow row);

    CommandDedupRow selectByKey(@Param("tenantId") String tenantId,
                                @Param("actorId") String actorId,
                                @Param("operation") String operation,
                                @Param("requestId") String requestId);

    int completeDedup(@Param("tenantId") String tenantId,
                      @Param("actorId") String actorId,
                      @Param("operation") String operation,
                      @Param("requestId") String requestId,
                      @Param("resultCode") String resultCode,
                      @Param("resourceType") String resourceType,
                      @Param("resourceId") String resourceId,
                      @Param("resourceRevision") Long resourceRevision,
                      @Param("responseJson") String responseJson,
                      @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
