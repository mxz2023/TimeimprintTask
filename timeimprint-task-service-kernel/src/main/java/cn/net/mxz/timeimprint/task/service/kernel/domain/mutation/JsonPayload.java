package cn.net.mxz.timeimprint.task.service.kernel.domain.mutation;

import java.util.Map;

/** 通用已校验 JSON 对象载荷。 */
public record JsonPayload(Map<String, Object> fields) implements ScenarioMutationPayload {
    public JsonPayload {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
}
