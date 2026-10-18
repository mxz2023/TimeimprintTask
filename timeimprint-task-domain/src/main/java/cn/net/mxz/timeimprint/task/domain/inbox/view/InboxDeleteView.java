package cn.net.mxz.timeimprint.task.domain.inbox.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** E31 成功结果。不含标题和正文。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record InboxDeleteView(
        @JsonProperty("inboxId") String inboxId,
        @JsonProperty("deletedAt") String deletedAt) {}
