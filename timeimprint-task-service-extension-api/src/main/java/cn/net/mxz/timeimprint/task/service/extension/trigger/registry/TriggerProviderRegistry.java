package cn.net.mxz.timeimprint.task.service.extension.trigger.registry;

import cn.net.mxz.timeimprint.task.service.extension.trigger.spi.TriggerProvider;
import java.util.Optional;

public interface TriggerProviderRegistry {

    Optional<TriggerProvider> find(TriggerProviderKey key);

    TriggerProvider require(TriggerProviderKey key);
}
