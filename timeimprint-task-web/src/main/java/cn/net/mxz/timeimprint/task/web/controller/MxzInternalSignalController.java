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

@RestController
@RequestMapping("/internal/v1")
public class MxzInternalSignalController {

    private final MxzTaskGateway gateway;

    public MxzInternalSignalController(MxzTaskGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/task-signals/{providerKey}")
    public MxzApiResponse<SignalAcceptedView> accept(
            @PathVariable String providerKey, @Valid @RequestBody InternalSignalRequest request) {
        return new MxzApiResponse<>(
                MxzApiErrorCodes.OK,
                "OK",
                UUID.randomUUID().toString().replace("-", ""),
                gateway.acceptSignal(providerKey, request));
    }

    /** T02 worker substitute for processing due signals by id. */
    @PostMapping("/task-signals/{signalId}/process")
    public MxzApiResponse<Void> process(@PathVariable long signalId) {
        gateway.processSignal(signalId);
        return new MxzApiResponse<>(
                MxzApiErrorCodes.OK, "OK", UUID.randomUUID().toString().replace("-", ""), null);
    }
}
