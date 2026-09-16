package cn.net.mxz.timeimprint.task.service.application.transition.port;

import cn.net.mxz.timeimprint.task.service.application.transition.model.TransitionRecord;
import java.util.List;

/** I05 transition diagnostics. */
public interface TransitionQuery {

    List<TransitionRecord> list(Long definitionId, Long instanceId, String cursor, int limit);
}
