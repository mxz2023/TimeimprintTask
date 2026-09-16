package cn.net.mxz.timeimprint.task.service.extension.materialization.registry;

import cn.net.mxz.timeimprint.task.service.extension.materialization.spi.ScenarioDataMaterializer;
import java.util.Optional;
import cn.net.mxz.timeimprint.task.service.extension.materialization.spi.ScenarioDataMaterializerKey;

public interface ScenarioDataMaterializerRegistry {

    Optional<ScenarioDataMaterializer> find(ScenarioDataMaterializerKey key);

    ScenarioDataMaterializer require(ScenarioDataMaterializerKey key);
}
