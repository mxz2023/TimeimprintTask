package cn.net.mxz.timeimprint.task.service.application.port;

import java.util.Optional;

/** 最小 Action 锁端口；执行细节见 {@link ActionJobExecutionPort}。 */
public interface ActionJobRepository {

    Optional<Long> findIdForUpdate(long actionJobId);
}
