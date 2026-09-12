package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.service.application.port.InstanceCommandPort;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzStorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.ActionJobMapper;
import java.time.Instant;
import org.springframework.stereotype.Repository;

/** Implements action cancellation for terminal instance commands (complete/skip). */
@Repository
public class MxzInstanceCommandPortImpl implements InstanceCommandPort {

    private final ActionJobMapper actionJobMapper;

    public MxzInstanceCommandPortImpl(ActionJobMapper actionJobMapper) {
        this.actionJobMapper = actionJobMapper;
    }

    @Override
    public void cancelRemainingActions(long instanceId, Instant now) {
        actionJobMapper.cancelReadyByInstance(
                instanceId,
                "INSTANCE_TERMINAL",
                MxzStorageTime.toUtcLdt(now),
                MxzStorageTime.toUtcLdt(now));
    }
}
