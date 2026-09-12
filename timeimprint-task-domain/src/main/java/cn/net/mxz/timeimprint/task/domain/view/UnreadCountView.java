package cn.net.mxz.timeimprint.task.domain.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UnreadCountView(
        @JsonProperty("unreadCount") int unreadCount,
        @JsonProperty("asOf") String asOf) {}
