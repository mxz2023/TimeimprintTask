package cn.net.mxz.timeimprint.task.adapter.feishu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 契约：出站客户端以 open_id、卡片 JSON、幂等 uuid 调用并返回受理号。 */
class FeishuMessageClientContractTest {

    @Test
    void returnsAcceptedMessageId() {
        FeishuMessageClient client = (openId, card, uuid) -> "om_" + openId + "_" + uuid;
        assertEquals("om_ou1_u1", client.sendInteractiveCard("ou1", "{}", "u1"));
    }
}
