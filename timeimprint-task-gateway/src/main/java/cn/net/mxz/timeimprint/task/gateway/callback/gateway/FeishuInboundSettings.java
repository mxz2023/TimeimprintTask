package cn.net.mxz.timeimprint.task.gateway.callback.gateway;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 飞书入站桥所需的最小配置视图：平台 {@code recipientId} → 飞书 {@code open_id} 映射（03 §3.1 recipient-map）。
 * 由组合根从运行配置构造；gateway 不依赖 notification 模块。
 *
 * @param recipientMap 平台 recipientId → open_id；null 视为空
 */
public record FeishuInboundSettings(Map<String, String> recipientMap) {

    public FeishuInboundSettings {
        Map<String, String> copy = new LinkedHashMap<>();
        if (recipientMap != null) {
            recipientMap.forEach((k, v) -> {
                if (k != null && !k.isBlank() && v != null && !v.isBlank()) {
                    copy.put(k.strip(), v.strip());
                }
            });
        }
        recipientMap = Map.copyOf(copy);
    }

    /** 反查：映射到该 open_id 的全部平台接收人；未映射返回空集合。 */
    public Set<String> platformUsersFor(String openId) {
        Set<String> out = new LinkedHashSet<>();
        if (openId == null || openId.isBlank()) {
            return out;
        }
        String id = openId.strip();
        recipientMap.forEach((user, mapped) -> {
            if (mapped.equals(id)) {
                out.add(user);
            }
        });
        return out;
    }
}
