package cn.net.mxz.timeimprint.task.domain.inbox.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** E32 成功结果。clearedCount 是本次新软删的已读行数。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record InboxClearReadView(@JsonProperty("clearedCount") int clearedCount) {}
