package cn.net.mxz.timeimprint.task.service.extension.materialization.spi;

import cn.net.mxz.timeimprint.task.service.extension.materialization.context.ScenarioDataMaterializationContext;
import cn.net.mxz.timeimprint.task.service.extension.materialization.spi.ScenarioDataMaterializerKey;

/** 在同一迁移事务内写入本场景专有表；返回实际受影响行数。 */
public interface ScenarioDataMaterializer {

    ScenarioDataMaterializerKey registrationKey();

    int materialize(ScenarioDataMaterializationContext context);
}
