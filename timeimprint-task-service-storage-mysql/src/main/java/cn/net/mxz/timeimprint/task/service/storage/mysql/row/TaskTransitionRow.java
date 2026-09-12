package cn.net.mxz.timeimprint.task.service.storage.mysql.row;

import java.time.LocalDateTime;

/** DB row for tt_task_transition. */
public class TaskTransitionRow {
    private Long transitionId;
    private Long definitionId;
    private Long instanceId;
    private String sourceType;
    private String sourceKey;
    private String commandKey;
    private String fromControlState;
    private String toControlState;
    private String fromLifecycle;
    private String toLifecycle;
    private String fromScenarioState;
    private String toScenarioState;
    private Long fromRevision;
    private Long toRevision;
    private String actorType;
    private String actorId;
    private String summaryJson;
    private String traceId;
    private LocalDateTime createdAt;

    public Long getTransitionId() { return transitionId; }
    public void setTransitionId(Long transitionId) { this.transitionId = transitionId; }
    public Long getDefinitionId() { return definitionId; }
    public void setDefinitionId(Long definitionId) { this.definitionId = definitionId; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getCommandKey() { return commandKey; }
    public void setCommandKey(String commandKey) { this.commandKey = commandKey; }
    public String getFromControlState() { return fromControlState; }
    public void setFromControlState(String fromControlState) { this.fromControlState = fromControlState; }
    public String getToControlState() { return toControlState; }
    public void setToControlState(String toControlState) { this.toControlState = toControlState; }
    public String getFromLifecycle() { return fromLifecycle; }
    public void setFromLifecycle(String fromLifecycle) { this.fromLifecycle = fromLifecycle; }
    public String getToLifecycle() { return toLifecycle; }
    public void setToLifecycle(String toLifecycle) { this.toLifecycle = toLifecycle; }
    public String getFromScenarioState() { return fromScenarioState; }
    public void setFromScenarioState(String fromScenarioState) { this.fromScenarioState = fromScenarioState; }
    public String getToScenarioState() { return toScenarioState; }
    public void setToScenarioState(String toScenarioState) { this.toScenarioState = toScenarioState; }
    public Long getFromRevision() { return fromRevision; }
    public void setFromRevision(Long fromRevision) { this.fromRevision = fromRevision; }
    public Long getToRevision() { return toRevision; }
    public void setToRevision(Long toRevision) { this.toRevision = toRevision; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getSummaryJson() { return summaryJson; }
    public void setSummaryJson(String summaryJson) { this.summaryJson = summaryJson; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
