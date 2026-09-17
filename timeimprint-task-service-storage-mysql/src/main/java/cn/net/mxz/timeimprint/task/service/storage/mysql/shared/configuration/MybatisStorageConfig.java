package cn.net.mxz.timeimprint.task.service.storage.mysql.shared.configuration;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(
        basePackages = {
            "cn.net.mxz.timeimprint.task.service.storage.mysql",
            "cn.net.mxz.timeimprint.task.service.capability.notification"
        },
        annotationClass = Mapper.class)
public class MybatisStorageConfig {}
