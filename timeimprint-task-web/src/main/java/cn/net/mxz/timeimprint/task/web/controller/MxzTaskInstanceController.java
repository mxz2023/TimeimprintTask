package cn.net.mxz.timeimprint.task.web.controller;

import cn.net.mxz.timeimprint.task.domain.MxzApiErrorCodes;
import cn.net.mxz.timeimprint.task.domain.MxzApiResponse;
import cn.net.mxz.timeimprint.task.domain.request.InstanceCommandRequest;
import cn.net.mxz.timeimprint.task.domain.view.CommandResultView;
import cn.net.mxz.timeimprint.task.domain.view.Page;
import cn.net.mxz.timeimprint.task.domain.view.TaskInstanceView;
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
 * 任务实例相关公开 HTTP 接口（契约 E07 / E08 / E09）。
 *
 * <p>路径前缀 {@code /api/v1}。S01 提醒触发后通常进入终态；S02 周期待办在 PENDING 下可执行
 * {@code complete} / {@code skip}（及场景支持的其他命令）。
 * 字段语义以 {@code docs/04-API.md} 为准。
 */
@RestController
@RequestMapping("/api/v1")
public class MxzTaskInstanceController {

    private final MxzTaskGateway gateway;

    public MxzTaskInstanceController(MxzTaskGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * E08 · 分页列出任务实例。
     *
     * <p><b>方法与路径：</b>{@code GET /api/v1/task-instances}
     *
     * <p><b>查询参数：</b>{@code definitionId}、{@code scenarioKey}、{@code lifecycleCategory}、
     * {@code scenarioState}、{@code participantRole}、{@code from}、{@code to}、{@code cursor}、{@code limit}
     *
     * <p><b>调用示例：</b>{@code curl -sS 'http://127.0.0.1:18080/api/v1/task-instances?definitionId=1'}
     */
    @GetMapping("/task-instances")
    public MxzApiResponse<Page<TaskInstanceView>> list(
            @RequestParam(required = false) Long definitionId,
            @RequestParam(required = false) String scenarioKey,
            @RequestParam(required = false) String lifecycleCategory,
            @RequestParam(required = false) String scenarioState,
            @RequestParam(required = false) String participantRole,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return new MxzApiResponse<>(
                MxzApiErrorCodes.OK,
                "OK",
                UUID.randomUUID().toString().replace("-", ""),
                gateway.listInstances(
                        definitionId,
                        scenarioKey,
                        lifecycleCategory,
                        scenarioState,
                        participantRole,
                        from,
                        to,
                        cursor,
                        limit));
    }

    /**
     * E07 · 按 ID 查询任务实例（场景状态、生命周期、修订号等）。
     *
     * <p><b>方法与路径：</b>{@code GET /api/v1/task-instances/{instanceId}}
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li>{@code instanceId} — 实例主键（库表 tt_task_instance.instance_id）</li>
     * </ul>
     *
     * <p><b>调用示例：</b>
     * <pre>{@code
     * curl -sS 'http://127.0.0.1:18080/api/v1/task-instances/1'
     * }</pre>
     */
    @GetMapping("/task-instances/{instanceId}")
    public MxzApiResponse<TaskInstanceView> get(@PathVariable long instanceId) {
        return new MxzApiResponse<>(
                MxzApiErrorCodes.OK, "OK", UUID.randomUUID().toString().replace("-", ""), gateway.getInstance(instanceId));
    }

    /**
     * E09 · 对实例执行场景命令（如 S02 的 {@code complete} / {@code skip} / {@code snooze}）。
     *
     * <p><b>方法与路径：</b>{@code POST /api/v1/task-instances/{instanceId}/commands/{commandKey}}
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li>{@code instanceId} — 实例主键</li>
     *   <li>{@code commandKey} — 命令键，由场景扩展声明（S02：{@code complete}、{@code skip} 等）</li>
     * </ul>
     *
     * <p><b>请求体参数（InstanceCommandRequest）：</b>
     * <ul>
     *   <li>{@code requestId} — 幂等键，标准 UUID 小写</li>
     *   <li>{@code expectedRevision} — 乐观锁期望修订号（来自 E07 的 revision）</li>
     *   <li>{@code commandSchemaVersion} — 命令 schema 版本，通常为 1</li>
     *   <li>{@code payload} — 命令载荷；{@code complete} 可为 {@code {}}；{@code skip} 需含 {@code reason}</li>
     * </ul>
     *
     * <p><b>调用示例（完成）：</b>
     * <pre>{@code
     * curl -sS -H 'Content-Type: application/json' -H 'Accept: application/json' \
     *   -X POST 'http://127.0.0.1:18080/api/v1/task-instances/1/commands/complete' -d "{
     *   \"requestId\": \"$(uuidgen | tr '[:upper:]' '[:lower:]')\",
     *   \"expectedRevision\": 2,
     *   \"commandSchemaVersion\": 1,
     *   \"payload\": {}
     * }"
     * }</pre>
     *
     * <p><b>调用示例（跳过）：</b>
     * <pre>{@code
     * curl -sS -H 'Content-Type: application/json' -H 'Accept: application/json' \
     *   -X POST 'http://127.0.0.1:18080/api/v1/task-instances/1/commands/skip' -d "{
     *   \"requestId\": \"$(uuidgen | tr '[:upper:]' '[:lower:]')\",
     *   \"expectedRevision\": 2,
     *   \"commandSchemaVersion\": 1,
     *   \"payload\": {\"reason\": \"本期不需要\"}
     * }"
     * }</pre>
     */
    @PostMapping("/task-instances/{instanceId}/commands/{commandKey}")
    public MxzApiResponse<CommandResultView> executeCommand(
            @PathVariable long instanceId,
            @PathVariable String commandKey,
            @Valid @RequestBody InstanceCommandRequest req) {
        return new MxzApiResponse<>(
                MxzApiErrorCodes.OK, "OK", UUID.randomUUID().toString().replace("-", ""),
                gateway.executeInstanceCommand(instanceId, commandKey, req));
    }
}
