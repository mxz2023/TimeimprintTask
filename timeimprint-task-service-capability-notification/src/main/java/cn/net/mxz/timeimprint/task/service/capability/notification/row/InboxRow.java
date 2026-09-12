package cn.net.mxz.timeimprint.task.service.capability.notification.row;

import java.time.LocalDateTime;

/** DB row for tt_inbox. */
public class InboxRow {
    private Long inboxId;
    private String tenantId;
    private Long notificationId;
    private Long actionJobId;
    private Long definitionId;
    private Long instanceId;
    private String recipientType;
    private String recipientId;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public Long getInboxId() { return inboxId; }
    public void setInboxId(Long inboxId) { this.inboxId = inboxId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long notificationId) { this.notificationId = notificationId; }
    public Long getActionJobId() { return actionJobId; }
    public void setActionJobId(Long actionJobId) { this.actionJobId = actionJobId; }
    public Long getDefinitionId() { return definitionId; }
    public void setDefinitionId(Long definitionId) { this.definitionId = definitionId; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public String getRecipientType() { return recipientType; }
    public void setRecipientType(String recipientType) { this.recipientType = recipientType; }
    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
