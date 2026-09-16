package cn.net.mxz.timeimprint.task.service.storage.mysql.shared.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({
    "cn.net.mxz.timeimprint.task.service.storage.mysql",
    "cn.net.mxz.timeimprint.task.service.capability.notification"
})
public class MybatisStorageConfig {}
