package cn.net.mxz.timeimprint.task.service.application.actor;

import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MxzLocalActorContextProvider implements ActorContextProvider {

    private final ActorContext fixed;

    public MxzLocalActorContextProvider(
            @Value("${timeimprint.local.tenant-id:local-tenant}") String tenantId,
            @Value("${timeimprint.local.actor-id:local-actor}") String actorId) {
        this.fixed = new ActorContext("USER", actorId, tenantId);
    }

    @Override
    public Optional<ActorContext> currentActor() {
        return Optional.of(fixed);
    }

    @Override
    public ActorContext requireCurrentActor() {
        return currentActor().orElseThrow(() -> new MxzApplicationException("UNAUTHENTICATED", "no actor"));
    }
}
