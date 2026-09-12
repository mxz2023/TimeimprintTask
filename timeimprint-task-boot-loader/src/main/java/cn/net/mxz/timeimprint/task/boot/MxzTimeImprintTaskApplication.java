package cn.net.mxz.timeimprint.task.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TimeImprintTask 启动入口；唯一组合根。
 */
@SpringBootApplication(scanBasePackages = "cn.net.mxz.timeimprint.task")
public class MxzTimeImprintTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(MxzTimeImprintTaskApplication.class, args);
    }
}
