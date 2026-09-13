package cn.net.mxz.timeimprint.task.web.controller;

import cn.net.mxz.timeimprint.task.domain.MxzApiErrorCodes;
import cn.net.mxz.timeimprint.task.domain.MxzApiResponse;
import cn.net.mxz.timeimprint.task.domain.request.RedriveRequest;
import cn.net.mxz.timeimprint.task.domain.view.ActionJobDiagnosticView;
import cn.net.mxz.timeimprint.task.domain.view.Page;
import cn.net.mxz.timeimprint.task.domain.view.SignalDiagnosticView;
import cn.net.mxz.timeimprint.task.domain.view.TransitionDiagnosticView;
import cn.net.mxz.timeimprint.task.gateway.MxzTaskGateway;
import jakarta.validation.Valid;
import java.util.UUID;
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

    /** I02 · 查询 Signal 诊断视图。 */
    @GetMapping("/task-signals/{signalId}")
    public MxzApiResponse<SignalDiagnosticView> getSignal(@PathVariable long signalId) {
        return ok(gateway.getSignal(signalId));
    }

    /** I03 · 分页查询 Action 积压。 */
    @GetMapping("/action-jobs")
    public MxzApiResponse<Page<ActionJobDiagnosticView>> listActionJobs(
            @RequestParam(required = false) Long definitionId,
            @RequestParam(required = false) Long instanceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String handlerKey,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return ok(gateway.listActionJobs(definitionId, instanceId, status, handlerKey, cursor, limit));
    }

    /** I04 · 查询单个 Action 及 attempt 摘要。 */
    @GetMapping("/action-jobs/{actionJobId}")
    public MxzApiResponse<ActionJobDiagnosticView> getActionJob(@PathVariable long actionJobId) {
        return ok(gateway.getActionJob(actionJobId));
    }

    /** I05 · 查询迁移链路。 */
    @GetMapping("/task-transitions")
    public MxzApiResponse<Page<TransitionDiagnosticView>> listTransitions(
            @RequestParam(required = false) Long definitionId,
            @RequestParam(required = false) Long instanceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return ok(gateway.listTransitions(definitionId, instanceId, cursor, limit));
    }

    /** I06 · 对 DEAD Signal 创建关联重驱行。 */
    @PostMapping("/task-signals/{signalId}/commands/redrive")
    public MxzApiResponse<SignalDiagnosticView> redriveSignal(
            @PathVariable long signalId, @Valid @RequestBody RedriveRequest request) {
        return ok(gateway.redriveSignal(signalId, request));
    }

    /** I07 · 对 LOCAL_TRANSACTIONAL 的 DEAD Action 创建关联重驱行。 */
    @PostMapping("/action-jobs/{actionJobId}/commands/redrive")
    public MxzApiResponse<ActionJobDiagnosticView> redriveAction(
            @PathVariable long actionJobId, @Valid @RequestBody RedriveRequest request) {
        return ok(gateway.redriveAction(actionJobId, request));
    }

    private static <T> MxzApiResponse<T> ok(T data) {
        return new MxzApiResponse<>(MxzApiErrorCodes.OK, "OK", UUID.randomUUID().toString().replace("-", ""), data);
    }
}
