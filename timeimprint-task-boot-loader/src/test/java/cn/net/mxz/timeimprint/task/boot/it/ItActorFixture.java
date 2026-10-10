package cn.net.mxz.timeimprint.task.boot.it;

import cn.net.mxz.timeimprint.task.service.application.access.model.ActorContext;
import cn.net.mxz.timeimprint.task.service.application.access.model.TestActorContextHolder;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** 进程内调用默认使用本地开发账号。HTTP 线程仍只认令牌。 */
public final class ItActorFixture implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        TestActorContextHolder.set(new ActorContext("USER", "local-actor", "local-tenant"));
    }
}
