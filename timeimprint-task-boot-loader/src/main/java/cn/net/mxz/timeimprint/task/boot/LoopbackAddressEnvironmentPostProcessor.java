package cn.net.mxz.timeimprint.task.boot;

import java.net.InetAddress;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * A39 / 03：local/test 禁止把 SERVER_ADDRESS（或 server.address）配成非回环；否则启动失败。
 */
public class LoopbackAddressEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!isLocalOrTest(environment)) {
            return;
        }
        String serverAddressProp = environment.getProperty("SERVER_ADDRESS");
        if (StringUtils.hasText(serverAddressProp)) {
            assertLoopback("SERVER_ADDRESS", serverAddressProp.trim());
        }
        // Also reject if any property source still forces a non-loopback server.address
        // before the local/test document hard-sets 127.0.0.1.
        String resolved = environment.getProperty("server.address");
        if (StringUtils.hasText(resolved)) {
            assertLoopback("server.address", resolved.trim());
        }
    }

    private static boolean isLocalOrTest(ConfigurableEnvironment environment) {
        for (String p : environment.getActiveProfiles()) {
            if ("local".equals(p) || "test".equals(p)) {
                return true;
            }
        }
        // Default profile is local (application.yml spring.profiles.default).
        if (environment.getActiveProfiles().length == 0) {
            String def = environment.getProperty("spring.profiles.default", "local");
            return "local".equals(def) || "test".equals(def);
        }
        return false;
    }

    public static void assertLoopback(String name, String address) {
        try {
            InetAddress inet = InetAddress.getByName(address);
            if (!inet.isLoopbackAddress()) {
                throw new IllegalStateException(
                        name + "=" + address + " is not a loopback address; local/test must bind 127.0.0.1");
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(
                    name + "=" + address + " cannot be resolved as a loopback address", ex);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
