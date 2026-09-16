package cn.net.mxz.timeimprint.task.service.application.shared.port;

import java.util.Optional;

public interface CommandDedupRepository {

    Optional<String> findCompletedResponseJson(
            String tenantId, String actorId, String operation, String requestId);

    /** 查幂等行（含 PROCESSING / COMPLETED），用于摘要冲突判定。 */
    Optional<CommandDedupSnapshot> find(
            String tenantId, String actorId, String operation, String requestId);

    /** @return true if this caller acquired PROCESSING lock */
    boolean tryBegin(
            String tenantId, String actorId, String operation, String requestId, byte[] requestHash);

    void complete(
            String tenantId,
            String actorId,
            String operation,
            String requestId,
            String resultCode,
            String resourceType,
            String resourceId,
            Long resourceRevision,
            String responseJson);

    record CommandDedupSnapshot(String processStatus, byte[] requestHash, String responseJson) {}
}
