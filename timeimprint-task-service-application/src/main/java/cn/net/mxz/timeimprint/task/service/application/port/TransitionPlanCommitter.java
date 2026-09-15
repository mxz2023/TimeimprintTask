package cn.net.mxz.timeimprint.task.service.application.port;

/**
 * 统一 TransitionPlan 校验与原子提交器；同步 Command 与 Signal 共享此端口。
 */
public interface TransitionPlanCommitter {

    TransitionCommitResult commit(TransitionCommitRequest request);
}
