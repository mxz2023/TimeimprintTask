package cn.net.mxz.timeimprint.task.boot.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TimeImprintTask 启动入口；唯一组合根。
 */
@SpringBootApplication(scanBasePackages = "cn.net.mxz.timeimprint.task")
public class TimeImprintTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(TimeImprintTaskApplication.class, args);
    }
}
