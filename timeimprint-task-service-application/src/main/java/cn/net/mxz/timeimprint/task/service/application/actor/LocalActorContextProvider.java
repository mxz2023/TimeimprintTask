package cn.net.mxz.timeimprint.task.service.application.actor;

import cn.net.mxz.timeimprint.task.service.application.exception.ApplicationException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class LocalActorContextProvider implements ActorContextProvider {

    private final ActorContext fixed;

    public LocalActorContextProvider(
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
        return currentActor().orElseThrow(() -> new ApplicationException("UNAUTHENTICATED", "no actor"));
    }
}
