package cn.net.mxz.timeimprint.task.gateway.callback.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FeishuInboundSettingsTest {

    @Test
    void reverseMapsOpenIdToEveryPlatformUser() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("local-actor", "ou_a");
        map.put("other", "ou_a");
        map.put("third", "ou_b");
        var settings = new FeishuInboundSettings(map);
        assertEquals(Set.of("local-actor", "other"), settings.platformUsersFor("ou_a"));
        assertEquals(Set.of("third"), settings.platformUsersFor(" ou_b "));
        assertTrue(settings.platformUsersFor("ou_missing").isEmpty());
        assertTrue(settings.platformUsersFor(null).isEmpty());
        assertTrue(settings.platformUsersFor(" ").isEmpty());
    }

    @Test
    void dropsBlankEntriesAndCopiesInput() {
        Map<String, String> map = new HashMap<>();
        map.put("u1", " ou_1 ");
        map.put(" ", "ou_2");
        map.put("u3", "");
        map.put("u4", null);
        var settings = new FeishuInboundSettings(map);
        map.put("late", "ou_late");
        assertEquals(Map.of("u1", "ou_1"), settings.recipientMap());
        assertTrue(new FeishuInboundSettings(null).recipientMap().isEmpty());
    }
}
