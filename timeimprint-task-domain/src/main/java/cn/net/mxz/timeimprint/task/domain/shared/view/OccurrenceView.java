package cn.net.mxz.timeimprint.task.domain.shared.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record OccurrenceView(
        @JsonProperty("occurrenceKey") String occurrenceKey,
        @JsonProperty("occurrenceAt") String occurrenceAt,
        @JsonProperty("dueAt") String dueAt) {}
