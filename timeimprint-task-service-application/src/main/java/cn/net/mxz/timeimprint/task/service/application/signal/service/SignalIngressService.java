package cn.net.mxz.timeimprint.task.service.application.signal.service;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.signal.model.SignalAcceptCommand;
import cn.net.mxz.timeimprint.task.service.application.signal.model.SignalAcceptResult;
import cn.net.mxz.timeimprint.task.service.application.signal.port.SignalIngressPort;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class SignalIngressService {

    private final ActorContextProvider actorContextProvider;
    private final SignalIngressPort signalIngressPort;
    private final TransactionBoundary tx;
    private final BusinessClock clock;

    public SignalIngressService(
            ActorContextProvider actorContextProvider,
            SignalIngressPort signalIngressPort,
            TransactionBoundary tx,
            BusinessClock clock) {
        this.actorContextProvider = actorContextProvider;
        this.signalIngressPort = signalIngressPort;
        this.tx = tx;
        this.clock = clock;
    }

    public SignalAcceptResult accept(
            String requestId,
            String providerKey,
            String signalKey,
            int schemaVersion,
            Instant occurredAt,
            long definitionId,
            Long instanceId,
            String payloadJson) {
        var actor = actorContextProvider.requireCurrentActor();
        return tx.execute(() -> signalIngressPort.accept(new SignalAcceptCommand(
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
