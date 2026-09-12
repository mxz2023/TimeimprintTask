package cn.net.mxz.timeimprint.task.service.application.service;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.actor.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.model.MxzSignalAcceptCommand;
import cn.net.mxz.timeimprint.task.service.application.model.MxzSignalAcceptResult;
import cn.net.mxz.timeimprint.task.service.application.port.SignalIngressPort;
import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class MxzSignalIngressService {

    private final ActorContextProvider actorContextProvider;
    private final SignalIngressPort signalIngressPort;
    private final TransactionBoundary tx;
    private final BusinessClock clock;

    public MxzSignalIngressService(
            ActorContextProvider actorContextProvider,
            SignalIngressPort signalIngressPort,
            TransactionBoundary tx,
            BusinessClock clock) {
        this.actorContextProvider = actorContextProvider;
        this.signalIngressPort = signalIngressPort;
        this.tx = tx;
        this.clock = clock;
    }

    public MxzSignalAcceptResult accept(
            String requestId,
            String providerKey,
            String signalKey,
            int schemaVersion,
            Instant occurredAt,
            long definitionId,
            Long instanceId,
            String payloadJson) {
        var actor = actorContextProvider.requireCurrentActor();
        return tx.execute(() -> signalIngressPort.accept(new MxzSignalAcceptCommand(
                actor.tenantKey(),
                actor.principalId(),
                requestId,
                providerKey,
                signalKey,
                schemaVersion,
                occurredAt,
                definitionId,
                instanceId,
                payloadJson,
                clock.nowUtcSeconds())));
    }
}
