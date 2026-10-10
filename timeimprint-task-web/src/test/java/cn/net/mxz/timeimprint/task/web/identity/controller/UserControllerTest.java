package cn.net.mxz.timeimprint.task.web.identity.controller;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
class UserControllerTest {
    @Test void exposesRegisterMapping() throws Exception {
        assertNotNull(UserController.class.getMethod("register", java.util.Map.class));
    }
}
