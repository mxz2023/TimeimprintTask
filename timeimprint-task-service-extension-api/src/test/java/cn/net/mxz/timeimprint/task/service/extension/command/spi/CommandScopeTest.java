package cn.net.mxz.timeimprint.task.service.extension.command.spi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Owner test: freeze CommandScope enum constants for P02 refactor safety. */
class CommandScopeTest {

    @Test
    void freezesEnumConstants() {
        String[] actual = Arrays.stream(CommandScope.values()).map(Enum::name).toArray(String[]::new);
        assertArrayEquals(new String[] {"DEFINITION",
                "INSTANCE"}, actual);
        assertEquals(2, CommandScope.values().length);
    }
}
