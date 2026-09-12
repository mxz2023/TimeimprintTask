package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.application.model.MxzSignalAcceptCommand;
import cn.net.mxz.timeimprint.task.service.application.model.MxzSignalAcceptResult;

public interface SignalIngressPort {

    MxzSignalAcceptResult accept(MxzSignalAcceptCommand command);
}
