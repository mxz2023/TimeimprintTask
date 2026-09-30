package cn.net.mxz.timeimprint.task.service.extension.action.result;

import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionHandlerOutcome;

public record ActionExecutionResult(ActionHandlerOutcome outcome, String outcomeCode, String safeSummary) {}
