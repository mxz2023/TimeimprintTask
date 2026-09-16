package cn.net.mxz.timeimprint.task.service.extension.shared.registry;

import cn.net.mxz.timeimprint.task.service.extension.action.registry.ActionHandlerRegistry;
import cn.net.mxz.timeimprint.task.service.extension.command.registry.TaskCommandHandlerRegistry;
import cn.net.mxz.timeimprint.task.service.extension.materialization.registry.ScenarioDataMaterializerRegistry;
import cn.net.mxz.timeimprint.task.service.extension.policy.registry.PolicyRegistry;
import cn.net.mxz.timeimprint.task.service.extension.scenario.registry.ScenarioExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.trigger.registry.TriggerProviderRegistry;
/** 五类扩展与物化器的聚合注册视图（启动时校验唯一键与版本可读性）。 */
public interface ExtensionRegistry {

    ScenarioExtensionRegistry scenarioExtensions();

    TriggerProviderRegistry triggerProviders();

    TaskCommandHandlerRegistry commandHandlers();

    ActionHandlerRegistry actionHandlers();

    PolicyRegistry policies();

    ScenarioDataMaterializerRegistry scenarioDataMaterializers();
}
