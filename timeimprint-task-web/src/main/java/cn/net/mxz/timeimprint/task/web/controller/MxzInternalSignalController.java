package cn.net.mxz.timeimprint.task.web.controller;

import cn.net.mxz.timeimprint.task.domain.MxzApiResponse;
import cn.net.mxz.timeimprint.task.domain.MxzApiResponses;
import cn.net.mxz.timeimprint.task.domain.request.InternalSignalRequest;
import cn.net.mxz.timeimprint.task.domain.view.SignalAcceptedView;
import cn.net.mxz.timeimprint.task.gateway.MxzTaskGateway;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部 Signal 接入与联调辅助接口（契约 I01；另含非正式 {@code process}）。
 *
 * <p>路径前缀 {@code /internal/v1}，供内部服务/测试调用，不是公开业务 API。
 * 字段语义以 {@code docs/04-API.md} 为准。
 */
@RestController
@RequestMapping("/internal/v1")
public class MxzInternalSignalController {

    private final MxzTaskGateway gateway;

    public MxzInternalSignalController(MxzTaskGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/task-signals/{providerKey}")
    public MxzApiResponse<SignalAcceptedView> accept(
            @PathVariable String providerKey, @Valid @RequestBody InternalSignalRequest request) {
        SignalAcceptedView accepted = gateway.acceptSignal(providerKey, request);
        String msg = accepted.duplicated()
                ? "Signal 已幂等接受（重复投递），signalId="
                        + accepted.signalId()
                        + "，processStatus="
                        + accepted.processStatus()
                : "Signal 已接受并入队，signalId="
                        + accepted.signalId()
                        + "，providerKey="
                        + providerKey
                        + "，processStatus="
                        + accepted.processStatus()
                        + "；可等待 Worker 或调用 process 推进";
        return MxzApiResponses.ok(msg, accepted);
    }

    @PostMapping("/task-signals/{signalId}/process")
    public MxzApiResponse<Void> process(@PathVariable long signalId) {
        gateway.processSignal(signalId);
        return MxzApiResponses.ok(
                "已手动处理 Signal，signalId=" + signalId + "；请再查询实例/收件确认场景状态", null);
    }
}
