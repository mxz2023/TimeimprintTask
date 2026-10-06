package cn.net.mxz.timeimprint.task.service.capability.notification.feishu.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class FeishuCardBuilderTest {

    private final FeishuCardBuilder builder = new FeishuCardBuilder();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void onlyRecurringTodoHasActions() {
        assertTrue(FeishuCardBuilder.hasActions("recurring_todo"));
        assertFalse(FeishuCardBuilder.hasActions("reminder"));
        assertFalse(FeishuCardBuilder.hasActions(null));
    }

    @Test
    void displayOnlyCardHasHeaderBodyAndNoActionElements() {
        JsonNode card = mapper.readTree(builder.build("T", "B", false, 0, 0, 0));
        assertEquals("2.0", card.path("schema").asString());
        assertEquals("T", card.path("header").path("title").path("content").asString());
        assertEquals(1, card.path("body").path("elements").size());
        assertFalse(card.toString().contains("button"));
    }

    @Test
    void blankTitleFallsBackAndBlankBodyIsOmitted() {
        JsonNode card = mapper.readTree(builder.build(" ", null, false, 0, 0, 0));
        assertEquals(FeishuCardBuilder.FALLBACK_TITLE, card.path("header").path("title").path("content").asString());
        assertEquals(0, card.path("body").path("elements").size());
    }

    @Test
    void actionCardHasThreeCallbackButtonsInOrder() {
        JsonNode card = mapper.readTree(builder.build("T", "B", true, 3, 11, 7));
        JsonNode actionRow = card.path("body").path("elements").get(1);
        assertEquals("small", actionRow.path("horizontal_spacing").asString());
        assertEquals("right", actionRow.path("horizontal_align").asString());
        JsonNode columns = actionRow.path("columns");
        assertEquals(3, columns.size());
        assertEquals("auto", columns.get(0).path("width").asString());
        assertEquals("完成", columns.get(0).path("elements").get(0).path("text").path("content").asString());
        assertEquals("跳过", columns.get(1).path("elements").get(0).path("text").path("content").asString());
        assertEquals("稍后提醒", columns.get(2).path("elements").get(0).path("text").path("content").asString());
        JsonNode snooze = columns.get(2).path("elements").get(0).path("behaviors").get(0).path("value");
        assertEquals("snooze", snooze.path("commandKey").asString());
        assertEquals(11, snooze.path("instanceId").asLong());
        assertEquals(3, snooze.path("definitionId").asLong());
        assertEquals(7, snooze.path("revision").asLong());
        assertFalse(snooze.has("snoozeUntil"));
    }
}
