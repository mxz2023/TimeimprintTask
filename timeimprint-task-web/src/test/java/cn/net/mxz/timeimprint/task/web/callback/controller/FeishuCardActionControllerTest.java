package cn.net.mxz.timeimprint.task.web.callback.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.net.mxz.timeimprint.task.gateway.callback.gateway.FeishuCardActionBridge;
import cn.net.mxz.timeimprint.task.web.callback.configuration.FeishuInboundEnabledCondition;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class FeishuCardActionControllerTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void freezesPublicSurfaceAndConditionalRegistration() {
        List<String> methods = Arrays.stream(FeishuCardActionController.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .toList();
        assertEquals(List.of("cardAction/4"), methods);
        assertEquals("/callbacks/v1/feishu/card-action", FeishuCardActionController.PATH);
        assertTrue(FeishuCardActionController.class.isAnnotationPresent(RestController.class));
        assertEquals(
                FeishuInboundEnabledCondition.class,
                FeishuCardActionController.class.getAnnotation(Conditional.class).value()[0]);
    }

    @Test
    void forwardsHeadersAndRawBodyAndReturnsFeishuToastJson() throws Exception {
        FeishuCardActionBridge bridge = mock(FeishuCardActionBridge.class);
        JsonNode toast = mapper.readTree("{\"toast\":{\"type\":\"success\",\"content\":\"已完成\"}}");
        when(bridge.handle(eq("1700"), eq("n1"), eq("sig"), any()))
                .thenReturn(new FeishuCardActionBridge.Reply(200, toast));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FeishuCardActionController(bridge)).build();

        byte[] raw = "{\"raw\":true}".getBytes(StandardCharsets.UTF_8);
        mvc.perform(MockMvcRequestBuilders.post("/callbacks/v1/feishu/card-action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Lark-Request-Timestamp", "1700")
                        .header("X-Lark-Request-Nonce", "n1")
                        .header("X-Lark-Signature", "sig")
                        .content(raw))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.toast.type").value("success"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.toast.content").value("已完成"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").doesNotExist());
        verify(bridge).handle(eq("1700"), eq("n1"), eq("sig"), eq(raw));
    }

    @Test
    void propagatesUnauthorizedStatusAndToleratesMissingSignatureHeaders() throws Exception {
        FeishuCardActionBridge bridge = mock(FeishuCardActionBridge.class);
        when(bridge.handle(eq(null), eq(null), eq(null), any()))
                .thenReturn(new FeishuCardActionBridge.Reply(401, mapper.readTree("{\"error\":\"invalid signature\"}")));
        ResponseEntity<JsonNode> direct = new FeishuCardActionController(bridge)
                .cardAction(null, null, null, "{}".getBytes(StandardCharsets.UTF_8));
        assertEquals(401, direct.getStatusCode().value());
        assertEquals("invalid signature", direct.getBody().path("error").asString());
    }
}
