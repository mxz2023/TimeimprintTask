package cn.net.mxz.timeimprint.task.web.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IT 专用探针：触发未捕获异常以验证统一信封与脱敏（非 04 正式端点）。
 *
 * <p>仅当 {@code timeimprint.http-boundary.probe=true} 时注册。
 */
@RestController
@RequestMapping("/api/v1/_http-boundary-probe")
@ConditionalOnProperty(name = "timeimprint.http-boundary.probe", havingValue = "true")
public class MxzHttpBoundaryProbeController {

    @GetMapping("/boom")
    public void boom() {
        throw new IllegalStateException("PROBE_SECRET jdbc:mysql://x password=leak stack must not appear");
    }
}
