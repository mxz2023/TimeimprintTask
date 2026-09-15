package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.application.model.SignalAcceptCommand;
import cn.net.mxz.timeimprint.task.service.application.model.SignalAcceptResult;

public interface SignalIngressPort {

    SignalAcceptResult accept(SignalAcceptCommand command);
}
