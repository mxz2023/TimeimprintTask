package cn.net.mxz.timeimprint.task.web.controller;

import cn.net.mxz.timeimprint.task.domain.MxzApiErrorCodes;
import cn.net.mxz.timeimprint.task.domain.MxzApiResponse;
import cn.net.mxz.timeimprint.task.domain.request.CreateTaskDefinitionRequest;
import cn.net.mxz.timeimprint.task.domain.request.DefinitionCommandRequest;
import cn.net.mxz.timeimprint.task.domain.request.PreviewRequest;
import cn.net.mxz.timeimprint.task.domain.view.CommandResultView;
import cn.net.mxz.timeimprint.task.domain.view.Page;
import cn.net.mxz.timeimprint.task.domain.view.PreviewResult;
import cn.net.mxz.timeimprint.task.domain.view.ScenarioMetadataView;
import cn.net.mxz.timeimprint.task.domain.view.TaskDefinitionView;
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
 * 任务定义相关公开 HTTP 接口（契约 E01 / E02 / E03 / E04 / E05 / E06）。
 *
 * <p>路径前缀 {@code /api/v1}。身份由本地 ActorContext 注入，请求体不得覆盖租户/操作者。
 * 字段语义以 {@code docs/04-API.md} 为准；联调示例见 {@code docs/phases/P01/MANUAL-HTTP.md}。
 */
@RestController
@RequestMapping("/api/v1")
public class MxzTaskDefinitionController {

    private final MxzTaskGateway gateway;

    public MxzTaskDefinitionController(MxzTaskGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * E01 · 列出已装配且可公开的场景元数据。
     *
     * <p><b>方法与路径：</b>{@code GET /api/v1/task-scenarios}
     *
     * <p><b>查询参数：</b>{@code cursor}、{@code limit}
     *
     * <p><b>调用示例：</b>{@code curl -sS 'http://127.0.0.1:18080/api/v1/task-scenarios'}
     */
    @GetMapping("/task-scenarios")
    public MxzApiResponse<Page<ScenarioMetadataView>> listScenarios(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return ok(gateway.listScenarios(cursor, limit));
    }

    /**
     * E02 · 预览触发发生时刻，不写入业务表。
     *
     * <p><b>方法与路径：</b>{@code POST /api/v1/task-definitions/preview}
     *
     * <p><b>请求体参数（PreviewRequest）：</b>
     * <ul>
     *   <li>{@code scenarioKey} — 场景键，如 {@code reminder} / {@code recurring_todo}</li>
     *   <li>{@code scenarioSchemaVersion} — 场景配置 schema 版本</li>
     *   <li>{@code scenarioConfig} — 场景配置 JSON（S01 可为 {@code {}}）</li>
     *   <li>{@code after} — 预览起点（ISO-8601 UTC，如 {@code 2026-09-01T00:00:00Z}）</li>
     *   <li>{@code limit} — 最多返回条数，1–100</li>
     *   <li>{@code triggerBindings} — 触发绑定列表（含 bindingKey / providerKey / schemaVersion / config）</li>
     * </ul>
     *
     * <p><b>调用示例：</b>
     * <pre>{@code
     * curl -sS -H 'Content-Type: application/json' -H 'Accept: application/json' \
     *   -X POST 'http://127.0.0.1:18080/api/v1/task-definitions/preview' -d '{
     *   "scenarioKey": "reminder",
     *   "scenarioSchemaVersion": 1,
     *   "scenarioConfig": {},
     *   "after": "2026-09-01T00:00:00Z",
     *   "limit": 10,
     *   "triggerBindings": [{
     *     "bindingKey": "primary",
     *     "providerKey": "calendar",
     *     "schemaVersion": 1,
     *     "config": {
     *       "type": "ONCE",
     *       "localDate": "2026-09-13",
     *       "localTime": "10:00:00",
     *       "zoneId": "Asia/Shanghai"
     *     }
     *   }]
     * }'
     * }</pre>
     */
    @PostMapping("/task-definitions/preview")
    public MxzApiResponse<PreviewResult> preview(@Valid @RequestBody PreviewRequest request) {
        return ok(gateway.preview(request));
    }

    /**
     * E03 · 创建任务定义（幂等：同一 requestId 重放返回同一 definitionId）。
     *
     * <p><b>方法与路径：</b>{@code POST /api/v1/task-definitions}
     *
     * <p><b>请求体参数（CreateTaskDefinitionRequest）：</b>
     * <ul>
     *   <li>{@code requestId} — 幂等键，标准 UUID 小写</li>
     *   <li>{@code scenarioKey} — 场景键</li>
     *   <li>{@code scenarioSchemaVersion} — 场景 schema 版本</li>
     *   <li>{@code title} — 标题（1–200）</li>
     *   <li>{@code description} — 描述（可选，最长 4000）</li>
     *   <li>{@code scenarioConfig} — 场景配置 JSON</li>
     *   <li>{@code participants} — 参与人（principalType / principalId / roleCode）</li>
     *   <li>{@code triggerBindings} — 触发绑定列表</li>
     * </ul>
     *
     * <p><b>调用示例：</b>
     * <pre>{@code
     * REQ=$(uuidgen | tr '[:upper:]' '[:lower:]')
     * curl -sS -H 'Content-Type: application/json' -H 'Accept: application/json' \
     *   -X POST 'http://127.0.0.1:18080/api/v1/task-definitions' -d "{
     *   \"requestId\": \"$REQ\",
     *   \"scenarioKey\": \"reminder\",
     *   \"scenarioSchemaVersion\": 1,
     *   \"title\": \"手动测试提醒\",
     *   \"description\": \"S01 ONCE\",
     *   \"scenarioConfig\": {},
     *   \"participants\": [
     *     {\"principalType\": \"USER\", \"principalId\": \"local-actor\", \"roleCode\": \"OWNER\"}
     *   ],
     *   \"triggerBindings\": [{
     *     \"bindingKey\": \"primary\",
     *     \"providerKey\": \"calendar\",
     *     \"schemaVersion\": 1,
     *     \"config\": {
     *       \"type\": \"ONCE\",
     *       \"localDate\": \"2026-09-13\",
     *       \"localTime\": \"10:00:00\",
     *       \"zoneId\": \"Asia/Shanghai\"
     *     }
     *   }]
     * }"
     * }</pre>
     */
    @PostMapping("/task-definitions")
    public MxzApiResponse<TaskDefinitionView> create(@Valid @RequestBody CreateTaskDefinitionRequest request) {
        return ok(gateway.create(request));
    }

    /**
     * E05 · 分页列出任务定义。
     *
     * <p><b>方法与路径：</b>{@code GET /api/v1/task-definitions}
     *
     * <p><b>查询参数：</b>{@code scenarioKey}、{@code controlState}、{@code participantRole}、{@code cursor}、{@code limit}
     *
     * <p><b>调用示例：</b>{@code curl -sS 'http://127.0.0.1:18080/api/v1/task-definitions?scenarioKey=reminder'}
     */
    @GetMapping("/task-definitions")
    public MxzApiResponse<Page<TaskDefinitionView>> listDefinitions(
            @RequestParam(required = false) String scenarioKey,
            @RequestParam(required = false) String controlState,
            @RequestParam(required = false) String participantRole,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return ok(gateway.listDefinitions(scenarioKey, controlState, participantRole, cursor, limit));
    }

    /**
     * E04 · 按 ID 查询任务定义详情。
     *
     * <p><b>方法与路径：</b>{@code GET /api/v1/task-definitions/{definitionId}}
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li>{@code definitionId} — 定义主键（创建接口返回的 data.definitionId）</li>
     * </ul>
     *
     * <p><b>调用示例：</b>
     * <pre>{@code
     * curl -sS 'http://127.0.0.1:18080/api/v1/task-definitions/1'
     * }</pre>
     */
    @GetMapping("/task-definitions/{definitionId}")
    public MxzApiResponse<TaskDefinitionView> get(@PathVariable long definitionId) {
        return ok(gateway.getDefinition(definitionId));
    }

    /**
     * E06 · 对定义执行控制命令：{@code pause} / {@code resume} / {@code retire}。
     *
     * <p><b>方法与路径：</b>{@code POST /api/v1/task-definitions/{definitionId}/commands/{commandKey}}
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li>{@code definitionId} — 定义主键</li>
     *   <li>{@code commandKey} — 命令键：{@code pause}、{@code resume}、{@code retire}</li>
     * </ul>
     *
     * <p><b>请求体参数（DefinitionCommandRequest）：</b>
     * <ul>
     *   <li>{@code requestId} — 幂等键，标准 UUID 小写</li>
     *   <li>{@code expectedRevision} — 乐观锁期望修订号（来自 E04 的 revision）</li>
     *   <li>{@code commandSchemaVersion} — 命令 schema 版本，通常为 1</li>
     *   <li>{@code payload} — 命令载荷 JSON（一期控制命令一般为 {@code {}}）</li>
     * </ul>
     *
     * <p><b>调用示例：</b>
     * <pre>{@code
     * curl -sS -H 'Content-Type: application/json' -H 'Accept: application/json' \
     *   -X POST 'http://127.0.0.1:18080/api/v1/task-definitions/1/commands/pause' -d "{
     *   \"requestId\": \"$(uuidgen | tr '[:upper:]' '[:lower:]')\",
     *   \"expectedRevision\": 1,
     *   \"commandSchemaVersion\": 1,
     *   \"payload\": {}
     * }"
     * }</pre>
     */
    @PostMapping("/task-definitions/{definitionId}/commands/{commandKey}")
    public MxzApiResponse<CommandResultView> executeCommand(
            @PathVariable long definitionId,
            @PathVariable String commandKey,
            @Valid @RequestBody DefinitionCommandRequest req) {
        return new MxzApiResponse<>(
                MxzApiErrorCodes.OK, "OK", UUID.randomUUID().toString().replace("-", ""),
                gateway.executeDefinitionCommand(definitionId, commandKey, req));
    }

    private static <T> MxzApiResponse<T> ok(T data) {
        return new MxzApiResponse<>(MxzApiErrorCodes.OK, "OK", UUID.randomUUID().toString().replace("-", ""), data);
    }
}
