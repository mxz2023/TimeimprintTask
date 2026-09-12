package cn.net.mxz.timeimprint.task.service.storage.mysql.row;

import java.time.LocalDateTime;

/** DB row for tt_task_instance. */
public class TaskInstanceRow {
    private Long instanceId;
    private Long definitionId;
    private Long triggerBindingId;
    private Long scheduleGeneration;
    private Long definitionControlGeneration;
    private String occurrenceKey;
    private LocalDateTime occurrenceAt;
    private LocalDateTime dueAt;
    private String lifecycleCategory;
    private String scenarioState;
    private Integer scenarioSchemaVersion;
    private String scenarioSnapshotJson;
    private byte[] snapshotHash;
    private String titleSnapshot;
    private String descriptionSnapshot;
    private Long revision;
    private LocalDateTime terminalAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Long getDefinitionId() { return definitionId; }
    public void setDefinitionId(Long definitionId) { this.definitionId = definitionId; }
    public Long getTriggerBindingId() { return triggerBindingId; }
    public void setTriggerBindingId(Long triggerBindingId) { this.triggerBindingId = triggerBindingId; }
    public Long getScheduleGeneration() { return scheduleGeneration; }
    public void setScheduleGeneration(Long scheduleGeneration) { this.scheduleGeneration = scheduleGeneration; }
    public Long getDefinitionControlGeneration() { return definitionControlGeneration; }
    public void setDefinitionControlGeneration(Long definitionControlGeneration) { this.definitionControlGeneration = definitionControlGeneration; }
    public String getOccurrenceKey() { return occurrenceKey; }
    public void setOccurrenceKey(String occurrenceKey) { this.occurrenceKey = occurrenceKey; }
    public LocalDateTime getOccurrenceAt() { return occurrenceAt; }
    public void setOccurrenceAt(LocalDateTime occurrenceAt) { this.occurrenceAt = occurrenceAt; }
    public LocalDateTime getDueAt() { return dueAt; }
    public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
    public String getLifecycleCategory() { return lifecycleCategory; }
    public void setLifecycleCategory(String lifecycleCategory) { this.lifecycleCategory = lifecycleCategory; }
    public String getScenarioState() { return scenarioState; }
    public void setScenarioState(String scenarioState) { this.scenarioState = scenarioState; }
    public Integer getScenarioSchemaVersion() { return scenarioSchemaVersion; }
    public void setScenarioSchemaVersion(Integer scenarioSchemaVersion) { this.scenarioSchemaVersion = scenarioSchemaVersion; }
    public String getScenarioSnapshotJson() { return scenarioSnapshotJson; }
    public void setScenarioSnapshotJson(String scenarioSnapshotJson) { this.scenarioSnapshotJson = scenarioSnapshotJson; }
    public byte[] getSnapshotHash() { return snapshotHash; }
    public void setSnapshotHash(byte[] snapshotHash) { this.snapshotHash = snapshotHash; }
    public String getTitleSnapshot() { return titleSnapshot; }
    public void setTitleSnapshot(String titleSnapshot) { this.titleSnapshot = titleSnapshot; }
    public String getDescriptionSnapshot() { return descriptionSnapshot; }
    public void setDescriptionSnapshot(String descriptionSnapshot) { this.descriptionSnapshot = descriptionSnapshot; }
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
    public LocalDateTime getTerminalAt() { return terminalAt; }
    public void setTerminalAt(LocalDateTime terminalAt) { this.terminalAt = terminalAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
