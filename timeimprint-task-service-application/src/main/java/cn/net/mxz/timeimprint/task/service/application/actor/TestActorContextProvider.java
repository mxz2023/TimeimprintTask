package cn.net.mxz.timeimprint.task.service.application.actor;

import cn.net.mxz.timeimprint.task.service.application.exception.ApplicationException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestActorContextProvider implements ActorContextProvider {

    private final ActorContext defaults;

    public TestActorContextProvider(
            @Value("${timeimprint.test.tenant-id:test-tenant}") String tenantId,
            @Value("${timeimprint.test.actor-id:test-actor}") String actorId) {
        this.defaults = new ActorContext("USER", actorId, tenantId);
    }

    @Override
    public Optional<ActorContext> currentActor() {
        return TestActorContextHolder.get().or(() -> Optional.of(defaults));
    }

    @Override
    public ActorContext requireCurrentActor() {
        return currentActor().orElseThrow(() -> new ApplicationException("UNAUTHENTICATED", "no actor"));
    }
}
