package cn.net.mxz.timeimprint.task.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MxzApiMessagesTest {

    @Test
    void commandNotSupportedIsChineseAndActionable() {
        String msg = MxzApiMessages.commandNotSupported("reminder", "complete");
        assertTrue(containsHan(msg), msg);
        assertTrue(msg.contains("complete"), msg);
        assertTrue(msg.contains("allowedCommands"), msg);
    }

    @Test
    void mapsEnglishLegacyDetail() {
        String msg = MxzApiMessages.error(
                MxzApiErrorCodes.COMMAND_NOT_SUPPORTED, "scenario does not declare commandKey=complete");
        assertTrue(containsHan(msg), msg);
        assertTrue(msg.contains("complete"), msg);
    }

    @Test
    void successMessageKeepsSummary() {
        assertEquals("已创建任务定义", MxzApiMessages.ok("已创建任务定义"));
    }

    private static boolean containsHan(String text) {
        return text.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }
}
