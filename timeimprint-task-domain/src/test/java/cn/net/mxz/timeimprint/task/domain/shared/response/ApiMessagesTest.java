package cn.net.mxz.timeimprint.task.domain.shared.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiMessagesTest {

    @Test
    void commandNotSupportedIsChineseAndActionable() {
        String msg = ApiMessages.commandNotSupported("reminder", "complete");
        assertTrue(containsHan(msg), msg);
        assertTrue(msg.contains("complete"), msg);
        assertTrue(msg.contains("allowedCommands"), msg);
    }

    @Test
    void mapsEnglishLegacyDetail() {
        String msg = ApiMessages.error(
                ApiErrorCodes.COMMAND_NOT_SUPPORTED, "scenario does not declare commandKey=complete");
        assertTrue(containsHan(msg), msg);
        assertTrue(msg.contains("complete"), msg);
    }

    @Test
    void successMessageKeepsSummary() {
        assertEquals("已创建任务定义", ApiMessages.ok("已创建任务定义"));
    }

    private static boolean containsHan(String text) {
        return text.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }
}
