import { api, newRequestId } from "./client.js";

/** 健康检查 */
export const health = {
  liveness: () => api.get("/actuator/health/liveness"),
  readiness: () => api.get("/actuator/health/readiness"),
};

/** E01 场景目录 */
export function listScenarios(query = {}) {
  return api.get("/api/v1/task-scenarios", query);
}

/**
 * 只提交契约字段。后端 FAIL_ON_UNKNOWN_PROPERTIES：
 * 若把 localDate/localTime/calType 等表单字段带到顶层会 400「未知字段」。
 */
function pickCreateBody(body) {
  return {
    requestId: body.requestId || newRequestId(),
    scenarioKey: body.scenarioKey,
    scenarioSchemaVersion: body.scenarioSchemaVersion,
    title: body.title,
    description: body.description ?? null,
    scenarioConfig: body.scenarioConfig ?? {},
    participants: body.participants,
    triggerBindings: body.triggerBindings,
  };
}

function pickPreviewBody(body) {
  return {
    scenarioKey: body.scenarioKey,
    scenarioSchemaVersion: body.scenarioSchemaVersion,
    scenarioConfig: body.scenarioConfig ?? {},
    triggerBindings: body.triggerBindings,
    after: body.after,
    limit: body.limit,
  };
}

/** E02 预览 */
export function previewDefinition(body) {
  return api.post("/api/v1/task-definitions/preview", pickPreviewBody(body));
}

/** E03 创建定义 */
export function createDefinition(body) {
  return api.post("/api/v1/task-definitions", pickCreateBody(body));
}

/** E04 */
export function getDefinition(definitionId) {
  return api.get(`/api/v1/task-definitions/${definitionId}`);
}

/** E05 */
export function listDefinitions(query = {}) {
  return api.get("/api/v1/task-definitions", query);
}

/** E06 */
export function executeDefinitionCommand(definitionId, commandKey, body) {
  return api.post(`/api/v1/task-definitions/${definitionId}/commands/${commandKey}`, {
    requestId: body.requestId || newRequestId(),
    expectedRevision: body.expectedRevision,
    commandSchemaVersion: body.commandSchemaVersion ?? 1,
    payload: body.payload ?? {},
  });
}

/** E07 */
export function getInstance(instanceId) {
  return api.get(`/api/v1/task-instances/${instanceId}`);
}

/** E08 */
export function listInstances(query = {}) {
  return api.get("/api/v1/task-instances", query);
}

/** E09 */
export function executeInstanceCommand(instanceId, commandKey, body) {
  return api.post(`/api/v1/task-instances/${instanceId}/commands/${commandKey}`, {
    requestId: body.requestId || newRequestId(),
    expectedRevision: body.expectedRevision,
    commandSchemaVersion: body.commandSchemaVersion ?? 1,
    payload: body.payload ?? {},
  });
}

/** E10 */
export function listInbox(query = {}) {
  return api.get("/api/v1/inbox", query);
}

/** E11 */
export function getInbox(inboxId) {
  return api.get(`/api/v1/inbox/${inboxId}`);
}

/** E12 */
export function unreadCount() {
  return api.get("/api/v1/inbox-unread-count");
}

/** E13 */
export function markInboxRead(inboxId, requestId) {
  return api.post(`/api/v1/inbox/${inboxId}/commands/mark-read`, {
    requestId: requestId || newRequestId(),
  });
}

/** I01 */
export function acceptSignal(providerKey, body) {
  return api.post(`/internal/v1/task-signals/${providerKey}`, {
    requestId: body.requestId || newRequestId(),
    ...body,
  });
}

/** 辅助 process（非正式编号） */
export function processSignal(signalId) {
  return api.post(`/internal/v1/task-signals/${signalId}/process`);
}

/** I02 */
export function getSignal(signalId) {
  return api.get(`/internal/v1/task-signals/${signalId}`);
}

/** I03 */
export function listActionJobs(query = {}) {
  return api.get("/internal/v1/action-jobs", query);
}

/** I04 */
export function getActionJob(actionJobId) {
  return api.get(`/internal/v1/action-jobs/${actionJobId}`);
}

/** I05 */
export function listTransitions(query = {}) {
  return api.get("/internal/v1/task-transitions", query);
}

/** I06 */
export function redriveSignal(signalId, body) {
  return api.post(`/internal/v1/task-signals/${signalId}/commands/redrive`, {
    requestId: body.requestId || newRequestId(),
    expectedStatus: body.expectedStatus || "DEAD",
    reason: body.reason || "manual redrive",
  });
}

/** I07 */
export function redriveAction(actionJobId, body) {
  return api.post(`/internal/v1/action-jobs/${actionJobId}/commands/redrive`, {
    requestId: body.requestId || newRequestId(),
    expectedStatus: body.expectedStatus || "DEAD",
    reason: body.reason || "manual redrive",
  });
}
