package cn.net.mxz.timeimprint.task.service.application.definition.port;

import cn.net.mxz.timeimprint.task.service.application.definition.model.CreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.definition.model.CreatedDefinitionResult;

/** 创建 ACTIVE 定义及其首窗 WAITING 实例与计划 Signal（同事务）。 */
public interface DefinitionCreatePort {

    CreatedDefinitionResult createActive(CreateDefinitionCommand command);
}
