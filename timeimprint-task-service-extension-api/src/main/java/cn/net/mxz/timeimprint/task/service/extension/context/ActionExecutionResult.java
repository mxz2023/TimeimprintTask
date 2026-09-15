package cn.net.mxz.timeimprint.task.service.extension.context;

import cn.net.mxz.timeimprint.task.service.extension.action.ActionHandlerOutcome;

public record ActionExecutionResult(ActionHandlerOutcome outcome, String outcomeCode, String safeSummary) {}
