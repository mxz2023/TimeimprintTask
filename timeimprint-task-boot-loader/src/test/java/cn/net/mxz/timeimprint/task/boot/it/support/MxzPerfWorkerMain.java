package cn.net.mxz.timeimprint.task.boot.it.support;

import cn.net.mxz.timeimprint.task.boot.MxzTimeImprintTaskApplication;
import org.springframework.boot.SpringApplication;

/**
 * 第二 JVM：开启 {@code @Scheduled} Worker/Reaper，与父进程共库跑 07 §6 性能门槛。
 *
 * <p>Args 透传给 Spring Boot（如 {@code --server.port=0}）。环境变量 / 系统属性需由父进程 ProcessBuilder 注入。
 */
public final class MxzPerfWorkerMain {

    private MxzPerfWorkerMain() {}

    public static void main(String[] args) {
        System.setProperty("spring.task.scheduling.enabled", "true");
        SpringApplication app = new SpringApplication(MxzTimeImprintTaskApplication.class);
        app.setAdditionalProfiles("local");
        app.run(args);
    }
}
