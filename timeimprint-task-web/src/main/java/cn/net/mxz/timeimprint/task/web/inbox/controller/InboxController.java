package cn.net.mxz.timeimprint.task.web.inbox.controller;

import cn.net.mxz.timeimprint.task.domain.shared.response.ApiResponse;
import cn.net.mxz.timeimprint.task.domain.shared.response.ApiResponses;
import cn.net.mxz.timeimprint.task.domain.shared.request.MarkReadRequest;
import cn.net.mxz.timeimprint.task.domain.inbox.view.InboxView;
import cn.net.mxz.timeimprint.task.domain.shared.view.Page;
import cn.net.mxz.timeimprint.task.domain.shared.view.UnreadCountView;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import jakarta.validation.Valid;
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
public class InboxController {

    private final TaskGateway gateway;

    public InboxController(TaskGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/inbox")
    public ApiResponse<Page<InboxView>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "50") int limit) {
        Page<InboxView> page = gateway.listInbox(unreadOnly, limit);
        int n = page.items() == null ? 0 : page.items().size();
        return ApiResponses.ok(
                unreadOnly
                        ? "已返回未读收件列表，本页 " + n + " 条"
                        : "已返回收件列表，本页 " + n + " 条",
                page);
    }

    @GetMapping("/inbox/{inboxId}")
    public ApiResponse<InboxView> get(@PathVariable long inboxId) {
        InboxView view = gateway.getInbox(inboxId);
        return ApiResponses.ok(
                "已查询收件详情，inboxId="
                        + view.inboxId()
                        + "，readAt="
                        + view.readAt()
                        + (view.readAt() == null ? "（未读，可 mark-read）" : "（已读）"),
                view);
    }

    @GetMapping("/inbox-unread-count")
    public ApiResponse<UnreadCountView> unread() {
        UnreadCountView view = gateway.unreadCount();
        return ApiResponses.ok("已返回未读收件数量，unreadCount=" + view.unreadCount(), view);
    }

    @PostMapping("/inbox/{inboxId}/commands/mark-read")
    public ApiResponse<InboxView> markRead(
            @PathVariable long inboxId, @Valid @RequestBody MarkReadRequest request) {
        InboxView view = gateway.markRead(inboxId, request);
        return ApiResponses.ok(
                "已将收件标记为已读（含幂等重放），inboxId="
                        + view.inboxId()
                        + "，readAt="
                        + view.readAt(),
                view);
    }
}
