package cn.net.mxz.timeimprint.task.service.runtime.shared.configuration;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.common.time.SystemUtcBusinessClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class RuntimeBeans {

    @Bean
    BusinessClock businessClock() {
        return new SystemUtcBusinessClock();
    }
}
