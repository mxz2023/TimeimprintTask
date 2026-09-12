package cn.net.mxz.timeimprint.task.service.extension.registry;

/** 五类扩展与物化器的聚合注册视图（启动时校验唯一键与版本可读性）。 */
public interface ExtensionRegistry {

    ScenarioExtensionRegistry scenarioExtensions();

    TriggerProviderRegistry triggerProviders();

    TaskCommandHandlerRegistry commandHandlers();

    ActionHandlerRegistry actionHandlers();

    PolicyRegistry policies();

    ScenarioDataMaterializerRegistry scenarioDataMaterializers();
}
