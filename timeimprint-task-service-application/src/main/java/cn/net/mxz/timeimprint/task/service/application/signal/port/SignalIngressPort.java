package cn.net.mxz.timeimprint.task.service.application.signal.port;

import cn.net.mxz.timeimprint.task.service.application.signal.model.SignalAcceptCommand;
import cn.net.mxz.timeimprint.task.service.application.signal.model.SignalAcceptResult;

public interface SignalIngressPort {

    SignalAcceptResult accept(SignalAcceptCommand command);
}
