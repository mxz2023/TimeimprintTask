package cn.net.mxz.timeimprint.task.web.controller;

import cn.net.mxz.timeimprint.task.domain.MxzApiErrorCodes;
import cn.net.mxz.timeimprint.task.domain.MxzApiResponse;
import cn.net.mxz.timeimprint.task.domain.request.CreateTaskDefinitionRequest;
import cn.net.mxz.timeimprint.task.domain.request.DefinitionCommandRequest;
import cn.net.mxz.timeimprint.task.domain.request.PreviewRequest;
import cn.net.mxz.timeimprint.task.domain.view.CommandResultView;
import cn.net.mxz.timeimprint.task.domain.view.PreviewResult;
import cn.net.mxz.timeimprint.task.domain.view.TaskDefinitionView;
import cn.net.mxz.timeimprint.task.gateway.MxzTaskGateway;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MxzTaskDefinitionController {

    private final MxzTaskGateway gateway;

    public MxzTaskDefinitionController(MxzTaskGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/task-definitions/preview")
    public MxzApiResponse<PreviewResult> preview(@Valid @RequestBody PreviewRequest request) {
        return ok(gateway.preview(request));
    }

    @PostMapping("/task-definitions")
    public MxzApiResponse<TaskDefinitionView> create(@Valid @RequestBody CreateTaskDefinitionRequest request) {
        return ok(gateway.create(request));
    }

    @GetMapping("/task-definitions/{definitionId}")
    public MxzApiResponse<TaskDefinitionView> get(@PathVariable long definitionId) {
        return ok(gateway.getDefinition(definitionId));
    }

    /** E06: pause / resume / retire a definition. */
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
