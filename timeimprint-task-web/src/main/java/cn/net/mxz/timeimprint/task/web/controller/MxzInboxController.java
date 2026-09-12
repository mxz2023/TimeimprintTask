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

@RestController
@RequestMapping("/api/v1")
public class MxzInboxController {

    private final MxzTaskGateway gateway;

    public MxzInboxController(MxzTaskGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/inbox")
    public MxzApiResponse<Page<InboxView>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "50") int limit) {
        return ok(gateway.listInbox(unreadOnly, limit));
    }

    @GetMapping("/inbox/{inboxId}")
    public MxzApiResponse<InboxView> get(@PathVariable long inboxId) {
        return ok(gateway.getInbox(inboxId));
    }

    @GetMapping("/inbox-unread-count")
    public MxzApiResponse<UnreadCountView> unread() {
        return ok(gateway.unreadCount());
    }

    @PostMapping("/inbox/{inboxId}/commands/mark-read")
    public MxzApiResponse<InboxView> markRead(
            @PathVariable long inboxId, @Valid @RequestBody MarkReadRequest request) {
        return ok(gateway.markRead(inboxId, request));
    }

    private static <T> MxzApiResponse<T> ok(T data) {
        return new MxzApiResponse<>(MxzApiErrorCodes.OK, "OK", UUID.randomUUID().toString().replace("-", ""), data);
    }
}
