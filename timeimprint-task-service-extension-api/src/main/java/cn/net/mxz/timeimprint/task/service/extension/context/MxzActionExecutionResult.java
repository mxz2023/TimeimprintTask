package cn.net.mxz.timeimprint.task.service.extension.context;

import cn.net.mxz.timeimprint.task.service.extension.action.ActionHandlerOutcome;

public record MxzActionExecutionResult(ActionHandlerOutcome outcome, String outcomeCode, String safeSummary) {}
