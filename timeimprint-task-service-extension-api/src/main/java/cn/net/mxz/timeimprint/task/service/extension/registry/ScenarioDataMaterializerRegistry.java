package cn.net.mxz.timeimprint.task.service.extension.registry;

import cn.net.mxz.timeimprint.task.service.extension.spi.ScenarioDataMaterializer;
import java.util.Optional;

public interface ScenarioDataMaterializerRegistry {

    Optional<ScenarioDataMaterializer> find(ScenarioDataMaterializerKey key);

    ScenarioDataMaterializer require(ScenarioDataMaterializerKey key);
}
