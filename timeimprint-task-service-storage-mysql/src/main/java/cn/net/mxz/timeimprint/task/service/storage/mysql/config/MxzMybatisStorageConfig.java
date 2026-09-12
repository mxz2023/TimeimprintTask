package cn.net.mxz.timeimprint.task.service.storage.mysql.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({
    "cn.net.mxz.timeimprint.task.service.storage.mysql.mapper",
    "cn.net.mxz.timeimprint.task.service.capability.notification.mapper"
})
public class MxzMybatisStorageConfig {}
