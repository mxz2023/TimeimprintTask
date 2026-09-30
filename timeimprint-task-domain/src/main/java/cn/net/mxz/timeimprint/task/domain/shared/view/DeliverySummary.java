package cn.net.mxz.timeimprint.task.domain.shared.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record DeliverySummary(
        @JsonProperty("deliveryState") String deliveryState,
        @JsonProperty("totalCount") int totalCount,
        @JsonProperty("readyCount") int readyCount,
        @JsonProperty("runningCount") int runningCount,
        @JsonProperty("retryWaitCount") int retryWaitCount,
        @JsonProperty("succeededCount") int succeededCount,
        @JsonProperty("deadCount") int deadCount,
        @JsonProperty("cancelledCount") int cancelledCount,
        @JsonProperty("expiredCount") int expiredCount,
        @JsonProperty("unknownCount") int unknownCount,
        @JsonProperty("inboxCount") int inboxCount,
        @JsonProperty("unreadInboxCount") int unreadInboxCount,
        @JsonProperty("updatedAt") String updatedAt) {}
