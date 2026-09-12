package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.application.model.MxzCreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.model.MxzCreatedDefinitionResult;

/** 创建 ACTIVE 定义及其首窗 WAITING 实例与计划 Signal（同事务）。 */
public interface DefinitionCreatePort {

    MxzCreatedDefinitionResult createActive(MxzCreateDefinitionCommand command);
}
