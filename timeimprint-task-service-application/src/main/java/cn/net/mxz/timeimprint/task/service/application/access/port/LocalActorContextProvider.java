package cn.net.mxz.timeimprint.task.service.application.access.port;

import cn.net.mxz.timeimprint.task.service.application.access.model.TestActorContextHolder;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import cn.net.mxz.timeimprint.task.service.application.access.model.ActorContext;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ActorContextProvider;

@Component
@Profile("!test")
public class LocalActorContextProvider implements ActorContextProvider {

    public LocalActorContextProvider() {}

    @Override
    public Optional<ActorContext> currentActor() {
        return TestActorContextHolder.get();
    }

    @Override
    public ActorContext requireCurrentActor() {
        return currentActor().orElseThrow(() -> new ApplicationException("UNAUTHENTICATED", "no actor"));
    }
}
