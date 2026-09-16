package cn.net.mxz.timeimprint.task.service.scenario.basic.recurringtodo.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.service.extension.command.spi.TaskCommandHandler;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Owner test for RecurringTodoCommandHandler holder: freezes nested command handler types.
 */
class RecurringTodoCommandHandlerTest {

    @Test
    void freezesNestedCommandHandlers() {
        List<String> nested = Arrays.stream(RecurringTodoCommandHandler.class.getDeclaredClasses())
                .filter(c -> java.lang.reflect.Modifier.isPublic(c.getModifiers()))
                .filter(c -> java.lang.reflect.Modifier.isStatic(c.getModifiers()))
                .map(Class::getSimpleName)
                .sorted()
                .collect(Collectors.toList());
        assertEquals(
                List.of(
                        "RecurringTodoCompleteHandler",
                        "RecurringTodoSkipHandler",
                        "RecurringTodoSnoozeHandler"),
                nested);
        for (Class<?> type : RecurringTodoCommandHandler.class.getDeclaredClasses()) {
            if (nested.contains(type.getSimpleName())) {
                assertTrue(TaskCommandHandler.class.isAssignableFrom(type), type.getName());
            }
        }
    }
}
