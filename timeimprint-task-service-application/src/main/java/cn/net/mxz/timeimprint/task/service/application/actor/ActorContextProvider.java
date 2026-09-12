package cn.net.mxz.timeimprint.task.service.application.actor;

import java.util.Optional;

public interface ActorContextProvider {

    Optional<ActorContext> currentActor();

    /** 公开写路径在缺少正式身份提供器时不得就绪。 */
    ActorContext requireCurrentActor();
}
