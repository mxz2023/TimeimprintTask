package cn.net.mxz.timeimprint.task.service.capability.notification.feishu.adapter;

import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 把通知标题/正文映射为飞书交互卡片 JSON（卡片 JSON 2.0）。
 *
 * <p>S02（recurring_todo）挂三个回调按钮 complete / skip / snooze，{@code action.value} 含
 * commandKey、instanceId、definitionId、revision（T04 入站使用）；S01（reminder）及其它场景仅展示，不挂按钮。
 * 「稍后提醒」不带时间：点击时由入站按业务时间 T+1h 计算。
 */
public final class FeishuCardBuilder {

    public static final String RECURRING_TODO_SCENARIO = "recurring_todo";
    public static final String COMMAND_COMPLETE = "complete";
    public static final String COMMAND_SKIP = "skip";
    public static final String COMMAND_SNOOZE = "snooze";
    static final String FALLBACK_TITLE = "任务提醒";

    private final JsonMapper mapper = JsonMapper.builder().build();

    /** 仅 S02 实例类场景有按钮。 */
    public static boolean hasActions(String scenarioKey) {
        return RECURRING_TODO_SCENARIO.equals(scenarioKey);
    }

    /**
     * @param withActions true 时追加 complete/skip/snooze 三按钮（须同时给出 ids 与 revision）
     * @return 卡片 JSON 字符串（作为飞书消息 {@code content}）
     */
    public String build(
            String title, String body, boolean withActions, long definitionId, long instanceId, long revision) {
        ObjectNode card = mapper.createObjectNode();
        card.put("schema", "2.0");
        card.putObject("config").put("update_multi", true);
        ObjectNode header = card.putObject("header");
        header.putObject("title")
                .put("tag", "plain_text")
                .put("content", title == null || title.isBlank() ? FALLBACK_TITLE : title);
        header.put("template", "blue");

        ArrayNode elements = card.putObject("body").putArray("elements");
        if (body != null && !body.isBlank()) {
            elements.addObject().put("tag", "markdown").put("content", body);
        }
        if (withActions) {
            // 等宽 weighted 会把三钮拉满整行、间距过大；改用 auto + 小间距，按钮按内容紧挨排列。
            ObjectNode set = elements.addObject();
            set.put("tag", "column_set").put("flex_mode", "none");
            set.put("horizontal_spacing", "small");
            set.put("horizontal_align", "right");
            ArrayNode columns = set.putArray("columns");
            List<String[]> buttons = List.of(
                    new String[] {"完成", "primary", COMMAND_COMPLETE},
                    new String[] {"跳过", "default", COMMAND_SKIP},
                    new String[] {"稍后提醒", "default", COMMAND_SNOOZE});
            for (String[] b : buttons) {
                ObjectNode column = columns.addObject();
                column.put("tag", "column").put("width", "auto");
                ObjectNode button = column.putArray("elements").addObject();
                button.put("tag", "button");
                button.putObject("text").put("tag", "plain_text").put("content", b[0]);
                button.put("type", b[1]);
                ObjectNode value = button.putArray("behaviors")
                        .addObject()
                        .put("type", "callback")
                        .putObject("value");
                value.put("commandKey", b[2]);
                value.put("instanceId", instanceId);
                value.put("definitionId", definitionId);
                value.put("revision", revision);
            }
        }
        return mapper.writeValueAsString(card);
    }
}
