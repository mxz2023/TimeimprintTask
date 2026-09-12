package cn.net.mxz.timeimprint.task.service.storage.mysql.row;

import java.time.LocalDateTime;

/** DB row for tt_trigger_binding. */
public class TriggerBindingRow {
    private Long triggerBindingId;
    private Long definitionId;
    private String bindingKey;
    private String providerKey;
    private Integer schemaVersion;
    private String configJson;
    private byte[] configHash;
    private String bindingState;
    private Long scheduleGeneration;
    private LocalDateTime nextFireAt;
    private String cursorJson;
    private Boolean exhausted;
    private Long revision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getTriggerBindingId() { return triggerBindingId; }
    public void setTriggerBindingId(Long triggerBindingId) { this.triggerBindingId = triggerBindingId; }
    public Long getDefinitionId() { return definitionId; }
    public void setDefinitionId(Long definitionId) { this.definitionId = definitionId; }
    public String getBindingKey() { return bindingKey; }
    public void setBindingKey(String bindingKey) { this.bindingKey = bindingKey; }
    public String getProviderKey() { return providerKey; }
    public void setProviderKey(String providerKey) { this.providerKey = providerKey; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public byte[] getConfigHash() { return configHash; }
    public void setConfigHash(byte[] configHash) { this.configHash = configHash; }
    public String getBindingState() { return bindingState; }
    public void setBindingState(String bindingState) { this.bindingState = bindingState; }
    public Long getScheduleGeneration() { return scheduleGeneration; }
    public void setScheduleGeneration(Long scheduleGeneration) { this.scheduleGeneration = scheduleGeneration; }
    public LocalDateTime getNextFireAt() { return nextFireAt; }
    public void setNextFireAt(LocalDateTime nextFireAt) { this.nextFireAt = nextFireAt; }
    public String getCursorJson() { return cursorJson; }
    public void setCursorJson(String cursorJson) { this.cursorJson = cursorJson; }
    public Boolean getExhausted() { return exhausted; }
    public void setExhausted(Boolean exhausted) { this.exhausted = exhausted; }
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
