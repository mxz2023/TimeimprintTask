package cn.net.mxz.timeimprint.task.service.extension.scenario.registry;

import cn.net.mxz.timeimprint.task.service.extension.scenario.spi.ScenarioExtension;
import java.util.List;
import java.util.Optional;

public interface ScenarioExtensionRegistry {

    Optional<ScenarioExtension> find(ScenarioExtensionKey key);

    ScenarioExtension require(ScenarioExtensionKey key);

    /** All registered scenario extensions (E01 catalog). */
    List<ScenarioExtension> listAll();
}
