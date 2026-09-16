package cn.net.mxz.timeimprint.task.service.application.shared.port;

import java.util.Optional;
import cn.net.mxz.timeimprint.task.service.application.access.model.ActorContext;

public interface ActorContextProvider {

    Optional<ActorContext> currentActor();

    /** 公开写路径在缺少正式身份提供器时不得就绪。 */
    ActorContext requireCurrentActor();
}
