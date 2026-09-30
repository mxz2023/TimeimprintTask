package cn.net.mxz.timeimprint.task.domain.shared.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Keyset pagination payload from {@code docs/04-API.md} section 3.6. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record Page<T>(
        @JsonProperty("items") List<T> items,
        @JsonProperty("nextCursor") String nextCursor,
        @JsonProperty("hasMore") boolean hasMore,
        @JsonProperty("asOf") String asOf) {}
