package cn.net.mxz.timeimprint.task.web.controller;

import cn.net.mxz.timeimprint.task.domain.MxzApiErrorCodes;
import cn.net.mxz.timeimprint.task.domain.MxzApiResponse;
import cn.net.mxz.timeimprint.task.domain.request.InternalSignalRequest;
import cn.net.mxz.timeimprint.task.domain.view.SignalAcceptedView;
import cn.net.mxz.timeimprint.task.gateway.MxzTaskGateway;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部 Signal 接入与联调辅助接口（契约 I01；另含非正式 {@code process}）。
 *
 * <p>路径前缀 {@code /internal/v1}，供内部服务/测试调用，不是公开业务 API。
 * 日历类 Signal 通常在创建定义时由平台写入；事件类可通过 I01 手动投递。
 * 字段语义以 {@code docs/04-API.md} 为准。
 */
@RestController
@RequestMapping("/internal/v1")
public class MxzInternalSignalController {

    private final MxzTaskGateway gateway;

    public MxzInternalSignalController(MxzTaskGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * I01 · 按触发提供者投递一条 Signal（接受后进入队列，由 Worker 异步处理）。
     *
     * <p><b>方法与路径：</b>{@code POST /internal/v1/task-signals/{providerKey}}
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li>{@code providerKey} — 触发提供者，如 {@code event}；日历计划 Signal 另有约束</li>
     * </ul>
     *
     * <p><b>请求体参数（InternalSignalRequest）：</b>
     * <ul>
     *   <li>{@code requestId} — 幂等键，标准 UUID 小写</li>
     *   <li>{@code signalKey} — Signal 业务唯一键（同 key 重放幂等）</li>
     *   <li>{@code schemaVersion} — 载荷 schema 版本，通常 ≥ 1</li>
     *   <li>{@code occurredAt} — 发生时间（ISO-8601 UTC）</li>
     *   <li>{@code subject} — 主体：{@code definitionId}、可选 {@code instanceId}</li>
     *   <li>{@code payload} — 提供者载荷 JSON</li>
     * </ul>
     *
     * <p><b>调用示例：</b>
     * <pre>{@code
     * curl -sS -H 'Content-Type: application/json' -H 'Accept: application/json' \
     *   -X POST 'http://127.0.0.1:18080/internal/v1/task-signals/event' -d "{
     *   \"requestId\": \"$(uuidgen | tr '[:upper:]' '[:lower:]')\",
     *   \"signalKey\": \"manual-event-$(uuidgen | tr '[:upper:]' '[:lower:]')\",
     *   \"schemaVersion\": 1,
     *   \"occurredAt\": \"2026-09-12T16:00:00Z\",
     *   \"subject\": {\"definitionId\": 1, \"instanceId\": 1},
     *   \"payload\": {}
     * }"
     * }</pre>
     */
    @PostMapping("/task-signals/{providerKey}")
    public MxzApiResponse<SignalAcceptedView> accept(
            @PathVariable String providerKey, @Valid @RequestBody InternalSignalRequest request) {
        return new MxzApiResponse<>(
                MxzApiErrorCodes.OK,
                "OK",
                UUID.randomUUID().toString().replace("-", ""),
                gateway.acceptSignal(providerKey, request));
    }

    /**
     * （辅助，非 04 正式编号）按 Signal ID 立即处理一条到期/就绪 Signal，便于本地联调。
     *
     * <p>生产路径应依赖 Worker 轮询；本接口用于不等待调度、手动推进状态机。
     *
     * <p><b>方法与路径：</b>{@code POST /internal/v1/task-signals/{signalId}/process}
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li>{@code signalId} — Signal 主键（库表 tt_task_signal.signal_id）</li>
     * </ul>
     *
     * <p>无请求体。
     *
     * <p><b>调用示例：</b>
     * <pre>{@code
     * curl -sS -H 'Content-Type: application/json' -H 'Accept: application/json' \
     *   -X POST 'http://127.0.0.1:18080/internal/v1/task-signals/1/process'
     * }</pre>
     */
    @PostMapping("/task-signals/{signalId}/process")
    public MxzApiResponse<Void> process(@PathVariable long signalId) {
        gateway.processSignal(signalId);
        return new MxzApiResponse<>(
                MxzApiErrorCodes.OK, "OK", UUID.randomUUID().toString().replace("-", ""), null);
    }
}
