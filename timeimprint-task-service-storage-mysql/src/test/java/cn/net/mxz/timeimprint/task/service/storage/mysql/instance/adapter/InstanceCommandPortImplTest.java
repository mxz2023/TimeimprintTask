package cn.net.mxz.timeimprint.task.service.storage.mysql.instance.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import cn.net.mxz.timeimprint.task.service.storage.mysql.action.mapper.ActionJobMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.action.row.ActionJobRow;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test for InstanceCommandPortImpl: public surface and primary-key lock order. */
class InstanceCommandPortImplTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(InstanceCommandPortImpl.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("cancelRemainingActions/2",
                "cancelRemainingActionsExceptTransition/3"), actual);
        assertFalse(actual.isEmpty());
    }

    @Test
    void cancelRemainingActionsLocksAndCancelsInPrimaryKeyOrder() {
        List<String> events = new ArrayList<>();
        Map<Long, String> status = Map.of(30L, "READY", 10L, "SUCCEEDED", 20L, "RETRY_WAIT");
        ActionJobMapper mapper = mapper(events, status, List.of(30L, 10L, 5L, 20L), List.of());
        new InstanceCommandPortImpl(mapper).cancelRemainingActions(7L, Instant.parse("2026-10-02T01:00:00Z"));
        assertEquals(
                List.of("select", "lock:5", "lock:10", "lock:20", "cancel:20", "lock:30", "cancel:30"),
                events);
    }

    @Test
    void cancelRemainingActionsExceptTransitionKeepsOnePlan() {
        List<String> events = new ArrayList<>();
        Map<Long, String> status = Map.of(8L, "READY", 2L, "READY");
        ActionJobMapper mapper = mapper(events, status, List.of(), List.of(8L, 2L));
        new InstanceCommandPortImpl(mapper)
                .cancelRemainingActionsExceptTransition(7L, 44L, Instant.parse("2026-10-02T01:00:00Z"));
        assertEquals(List.of("selectExcept:44", "lock:2", "cancel:2", "lock:8", "cancel:8"), events);
    }

    private static ActionJobMapper mapper(
            List<String> events,
            Map<Long, String> status,
            List<Long> remaining,
            List<Long> exceptTransition) {
        return (ActionJobMapper) Proxy.newProxyInstance(
                ActionJobMapper.class.getClassLoader(),
                new Class<?>[] {ActionJobMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectReadyIdsByInstance" -> {
                        events.add("select");
                        yield remaining;
                    }
                    case "selectReadyIdsByInstanceExceptTransition" -> {
                        events.add("selectExcept:" + args[1]);
                        yield exceptTransition;
                    }
                    case "selectByIdForUpdate" -> {
                        long id = (Long) args[0];
                        events.add("lock:" + id);
                        if (!status.containsKey(id)) {
                            yield null;
                        }
                        ActionJobRow row = new ActionJobRow();
                        row.setStatus(status.get(id));
                        yield row;
                    }
                    case "cancelReadyById" -> {
                        events.add("cancel:" + args[0]);
                        yield 1;
                    }
                    default -> defaultValue(method);
                });
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (type.equals(boolean.class)) {
            return false;
        }
        if (type.equals(int.class)) {
            return 0;
        }
        if (type.equals(long.class)) {
            return 0L;
        }
        if (List.class.isAssignableFrom(type)) {
            return List.of();
        }
        return null;
    }
}
