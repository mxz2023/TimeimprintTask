package cn.net.mxz.timeimprint.task.service.storage.mysql.shared.configuration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Owner context test for MybatisStorageConfig. */
class MybatisStorageConfigContextTest {

    @Test
    void freezesMapperScanPackages() {
        assertTrue(MybatisStorageConfig.class.isAnnotationPresent(Configuration.class));
        MapperScan scan = MybatisStorageConfig.class.getAnnotation(MapperScan.class);
        assertArrayEquals(
                new String[] {
                    "cn.net.mxz.timeimprint.task.service.storage.mysql",
                    "cn.net.mxz.timeimprint.task.service.capability.notification"
                },
                scan.value());
    }
}
