package cn.net.mxz.timeimprint.task.service.storage.mysql.instance.adapter;

import cn.net.mxz.timeimprint.task.service.application.instance.port.InstanceCommandPort;
import cn.net.mxz.timeimprint.task.service.storage.mysql.action.row.ActionJobRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.shared.time.StorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.action.mapper.ActionJobMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Cancels unstarted actions for instance commands.
 * Multiple action rows are locked one by one in {@code action_job_id} order so two
 * transactions cannot wait on each other.
 */
@Repository
public class InstanceCommandPortImpl implements InstanceCommandPort {

    private final ActionJobMapper actionJobMapper;

    public InstanceCommandPortImpl(ActionJobMapper actionJobMapper) {
        this.actionJobMapper = actionJobMapper;
    }

    @Override
    public void cancelRemainingActions(long instanceId, Instant now) {
        cancelInPrimaryKeyOrder(actionJobMapper.selectReadyIdsByInstance(instanceId), now);
    }

    @Override
    public void cancelRemainingActionsExceptTransition(
            long instanceId, long keepTransitionId, Instant now) {
        cancelInPrimaryKeyOrder(
                actionJobMapper.selectReadyIdsByInstanceExceptTransition(instanceId, keepTransitionId),
                now);
    }

    private void cancelInPrimaryKeyOrder(List<Long> actionJobIds, Instant now) {
        List<Long> ordered = new ArrayList<>(actionJobIds);
        ordered.sort(Long::compareTo);
        LocalDateTime at = StorageTime.toUtcLdt(now);
        for (Long actionJobId : ordered) {
            ActionJobRow locked = actionJobMapper.selectByIdForUpdate(actionJobId);
            if (locked == null) {
                continue;
            }
            if (!"READY".equals(locked.getStatus()) && !"RETRY_WAIT".equals(locked.getStatus())) {
                continue;
            }
            actionJobMapper.cancelReadyById(actionJobId, "INSTANCE_TERMINAL", at, at);
        }
    }
}
