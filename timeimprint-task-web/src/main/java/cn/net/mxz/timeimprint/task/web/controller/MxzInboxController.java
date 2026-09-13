package cn.net.mxz.timeimprint.task.web.controller;

import cn.net.mxz.timeimprint.task.domain.MxzApiErrorCodes;
import cn.net.mxz.timeimprint.task.domain.MxzApiResponse;
import cn.net.mxz.timeimprint.task.domain.request.MarkReadRequest;
import cn.net.mxz.timeimprint.task.domain.view.InboxView;
import cn.net.mxz.timeimprint.task.domain.view.Page;
import cn.net.mxz.timeimprint.task.domain.view.UnreadCountView;
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
 * 站内收件箱相关公开 HTTP 接口（契约 E10 / E11 / E12 / E13）。
 *
 * <p>路径前缀 {@code /api/v1}。收件在 Action（如 IN_APP 通知）成功后写入；
 * 查询范围受当前 ActorContext 身份约束。字段语义以 {@code docs/04-API.md} 为准。
 */
@RestController
@RequestMapping("/api/v1")
public class MxzInboxController {

    private final MxzTaskGateway gateway;

    public MxzInboxController(MxzTaskGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * E10 · 分页列出当前用户收件（可只看未读）。
     *
     * <p><b>方法与路径：</b>{@code GET /api/v1/inbox}
     *
     * <p><b>查询参数：</b>
     * <ul>
     *   <li>{@code unreadOnly} — 是否仅未读，默认 {@code false}</li>
     *   <li>{@code limit} — 返回条数上限，默认 {@code 50}</li>
     * </ul>
     *
     * <p><b>调用示例：</b>
     * <pre>{@code
     * curl -sS 'http://127.0.0.1:18080/api/v1/inbox?unreadOnly=true&limit=20'
     * }</pre>
     */
    @GetMapping("/inbox")
    public MxzApiResponse<Page<InboxView>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "50") int limit) {
        return ok(gateway.listInbox(unreadOnly, limit));
    }

    /**
     * E11 · 按 ID 查询单条收件详情。
     *
     * <p><b>方法与路径：</b>{@code GET /api/v1/inbox/{inboxId}}
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li>{@code inboxId} — 收件主键（列表项 data.items[].inboxId）</li>
     * </ul>
     *
     * <p><b>调用示例：</b>
     * <pre>{@code
     * curl -sS 'http://127.0.0.1:18080/api/v1/inbox/1'
     * }</pre>
     */
    @GetMapping("/inbox/{inboxId}")
    public MxzApiResponse<InboxView> get(@PathVariable long inboxId) {
        return ok(gateway.getInbox(inboxId));
    }

    /**
     * E12 · 查询当前用户未读收件数量。
     *
     * <p><b>方法与路径：</b>{@code GET /api/v1/inbox-unread-count}
     *
     * <p>无路径/查询参数。
     *
     * <p><b>调用示例：</b>
     * <pre>{@code
     * curl -sS 'http://127.0.0.1:18080/api/v1/inbox-unread-count'
     * }</pre>
     */
    @GetMapping("/inbox-unread-count")
    public MxzApiResponse<UnreadCountView> unread() {
        return ok(gateway.unreadCount());
    }

    /**
     * E13 · 将指定收件标记为已读（幂等：同一 requestId 重放保留首次 readAt）。
     *
     * <p><b>方法与路径：</b>{@code POST /api/v1/inbox/{inboxId}/commands/mark-read}
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li>{@code inboxId} — 收件主键</li>
     * </ul>
     *
     * <p><b>请求体参数（MarkReadRequest）：</b>
     * <ul>
     *   <li>{@code requestId} — 幂等键，标准 UUID 小写</li>
     * </ul>
     *
     * <p><b>调用示例：</b>
     * <pre>{@code
     * curl -sS -H 'Content-Type: application/json' -H 'Accept: application/json' \
     *   -X POST 'http://127.0.0.1:18080/api/v1/inbox/1/commands/mark-read' \
     *   -d "{\"requestId\":\"$(uuidgen | tr '[:upper:]' '[:lower:]')\"}"
     * }</pre>
     */
    @PostMapping("/inbox/{inboxId}/commands/mark-read")
    public MxzApiResponse<InboxView> markRead(
            @PathVariable long inboxId, @Valid @RequestBody MarkReadRequest request) {
        return ok(gateway.markRead(inboxId, request));
    }

    private static <T> MxzApiResponse<T> ok(T data) {
        return new MxzApiResponse<>(MxzApiErrorCodes.OK, "OK", UUID.randomUUID().toString().replace("-", ""), data);
    }
}
