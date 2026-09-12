package cn.net.mxz.timeimprint.task.domain.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record InboxView(
        @JsonProperty("inboxId") String inboxId,
        @JsonProperty("notificationId") String notificationId,
        @JsonProperty("actionJobId") String actionJobId,
        @JsonProperty("definitionId") String definitionId,
        @JsonProperty("instanceId") String instanceId,
        @JsonProperty("scenarioKey") String scenarioKey,
        @JsonProperty("purpose") String purpose,
        @JsonProperty("title") String title,
        @JsonProperty("body") String body,
        @JsonProperty("readAt") String readAt,
        @JsonProperty("createdAt") String createdAt) {}
