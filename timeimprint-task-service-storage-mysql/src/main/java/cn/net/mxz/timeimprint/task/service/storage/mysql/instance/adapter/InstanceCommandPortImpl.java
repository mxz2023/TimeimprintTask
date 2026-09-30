package cn.net.mxz.timeimprint.task.service.storage.mysql.instance.adapter;

import cn.net.mxz.timeimprint.task.service.application.instance.port.InstanceCommandPort;
import cn.net.mxz.timeimprint.task.service.storage.mysql.shared.time.StorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.action.mapper.ActionJobMapper;
import java.time.Instant;
import org.springframework.stereotype.Repository;

/** Implements action cancellation for terminal instance commands (complete/skip). */
@Repository
public class InstanceCommandPortImpl implements InstanceCommandPort {

    private final ActionJobMapper actionJobMapper;

    public InstanceCommandPortImpl(ActionJobMapper actionJobMapper) {
        this.actionJobMapper = actionJobMapper;
    }

    @Override
    public void cancelRemainingActions(long instanceId, Instant now) {
        actionJobMapper.cancelReadyByInstance(
                instanceId,
                "INSTANCE_TERMINAL",
                StorageTime.toUtcLdt(now),
                StorageTime.toUtcLdt(now));
    }

    @Override
    public void cancelRemainingActionsExceptTransition(
            long instanceId, long keepTransitionId, Instant now) {
        actionJobMapper.cancelReadyByInstanceExceptTransition(
                instanceId,
                keepTransitionId,
                "INSTANCE_TERMINAL",
                StorageTime.toUtcLdt(now),
                StorageTime.toUtcLdt(now));
    }
}
