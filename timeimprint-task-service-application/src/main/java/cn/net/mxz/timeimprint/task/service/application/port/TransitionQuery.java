package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.application.model.MxzTransitionRecord;
import java.util.List;

/** I05 transition diagnostics. */
public interface TransitionQuery {

    List<MxzTransitionRecord> list(Long definitionId, Long instanceId, String cursor, int limit);
}
