package cn.net.mxz.timeimprint.task.web.controller;

import cn.net.mxz.timeimprint.task.domain.MxzApiErrorCodes;
import cn.net.mxz.timeimprint.task.domain.MxzApiResponse;
import cn.net.mxz.timeimprint.task.domain.request.InstanceCommandRequest;
import cn.net.mxz.timeimprint.task.domain.view.CommandResultView;
import cn.net.mxz.timeimprint.task.domain.view.TaskInstanceView;
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
public class MxzTaskInstanceController {

    private final MxzTaskGateway gateway;

    public MxzTaskInstanceController(MxzTaskGateway gateway) {
        this.gateway = gateway;
    }

    /** E07: get instance by ID. */
    @GetMapping("/task-instances/{instanceId}")
    public MxzApiResponse<TaskInstanceView> get(@PathVariable long instanceId) {
        return new MxzApiResponse<>(
                MxzApiErrorCodes.OK, "OK", UUID.randomUUID().toString().replace("-", ""), gateway.getInstance(instanceId));
    }

    /** E09: execute an instance-level scenario command (complete, skip, snooze…). */
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
