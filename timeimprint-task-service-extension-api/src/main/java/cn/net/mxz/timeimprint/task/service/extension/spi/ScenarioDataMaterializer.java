package cn.net.mxz.timeimprint.task.service.extension.spi;

import cn.net.mxz.timeimprint.task.service.extension.context.MxzScenarioDataMaterializationContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioDataMaterializerKey;

/** 在同一迁移事务内写入本场景专有表；返回实际受影响行数。 */
public interface ScenarioDataMaterializer {

    ScenarioDataMaterializerKey registrationKey();

    int materialize(MxzScenarioDataMaterializationContext context);
}
