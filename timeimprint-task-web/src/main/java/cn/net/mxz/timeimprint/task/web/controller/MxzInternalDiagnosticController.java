package cn.net.mxz.timeimprint.task.web.controller;

import cn.net.mxz.timeimprint.task.domain.MxzApiResponse;
import cn.net.mxz.timeimprint.task.domain.MxzApiResponses;
import cn.net.mxz.timeimprint.task.domain.request.RedriveRequest;
import cn.net.mxz.timeimprint.task.domain.view.ActionJobDiagnosticView;
import cn.net.mxz.timeimprint.task.domain.view.Page;
import cn.net.mxz.timeimprint.task.domain.view.SignalDiagnosticView;
import cn.net.mxz.timeimprint.task.domain.view.TransitionDiagnosticView;
import cn.net.mxz.timeimprint.task.gateway.MxzTaskGateway;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部诊断与重驱接口（契约 I02—I07）。
 *
 * <p>路径前缀 {@code /internal/v1}。字段语义以 {@code docs/04-API.md} 为准。
 */
@RestController
@RequestMapping("/internal/v1")
public class MxzInternalDiagnosticController {

    private final MxzTaskGateway gateway;

    public MxzInternalDiagnosticController(MxzTaskGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/task-signals/{signalId}")
    public MxzApiResponse<SignalDiagnosticView> getSignal(@PathVariable long signalId) {
        SignalDiagnosticView view = gateway.getSignal(signalId);
        return MxzApiResponses.ok(
                "已查询 Signal 诊断，signalId="
                        + view.signalId()
                        + "，processStatus="
                        + view.processStatus(),
                view);
    }

    @GetMapping("/action-jobs")
    public MxzApiResponse<Page<ActionJobDiagnosticView>> listActionJobs(
            @RequestParam(required = false) Long definitionId,
            @RequestParam(required = false) Long instanceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String handlerKey,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        Page<ActionJobDiagnosticView> page =
                gateway.listActionJobs(definitionId, instanceId, status, handlerKey, cursor, limit);
        int n = page.items() == null ? 0 : page.items().size();
        return MxzApiResponses.ok("已返回 Action 诊断列表，本页 " + n + " 条", page);
    }

    @GetMapping("/action-jobs/{actionJobId}")
    public MxzApiResponse<ActionJobDiagnosticView> getActionJob(@PathVariable long actionJobId) {
        ActionJobDiagnosticView view = gateway.getActionJob(actionJobId);
        return MxzApiResponses.ok(
                "已查询 Action 诊断，actionJobId="
                        + view.actionJobId()
                        + "，effectiveStatus="
                        + view.effectiveStatus(),
                view);
    }

    @GetMapping("/task-transitions")
    public MxzApiResponse<Page<TransitionDiagnosticView>> listTransitions(
            @RequestParam(required = false) Long definitionId,
            @RequestParam(required = false) Long instanceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        Page<TransitionDiagnosticView> page = gateway.listTransitions(definitionId, instanceId, cursor, limit);
        int n = page.items() == null ? 0 : page.items().size();
        return MxzApiResponses.ok("已返回迁移链路列表，本页 " + n + " 条", page);
    }

    @PostMapping("/task-signals/{signalId}/commands/redrive")
    public MxzApiResponse<SignalDiagnosticView> redriveSignal(
            @PathVariable long signalId, @Valid @RequestBody RedriveRequest request) {
        SignalDiagnosticView view = gateway.redriveSignal(signalId, request);
        return MxzApiResponses.ok(
                "已对 DEAD Signal 发起重驱，signalId="
                        + view.signalId()
                        + "，processStatus="
                        + view.processStatus()
                        + "，redriveNo="
                        + view.redriveNo(),
                view);
    }

    @PostMapping("/action-jobs/{actionJobId}/commands/redrive")
    public MxzApiResponse<ActionJobDiagnosticView> redriveAction(
            @PathVariable long actionJobId, @Valid @RequestBody RedriveRequest request) {
        ActionJobDiagnosticView view = gateway.redriveAction(actionJobId, request);
        return MxzApiResponses.ok(
                "已对 DEAD Action 发起重驱，actionJobId="
                        + view.actionJobId()
                        + "，effectiveStatus="
                        + view.effectiveStatus()
                        + "，redriveNo="
                        + view.redriveNo(),
                view);
    }
}
