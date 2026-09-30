package cn.net.mxz.timeimprint.task.service.application.inbox.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze InboxRepository method surface for P02 refactor safety. */
class InboxRepositoryContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(InboxRepository.class.isInterface());
        List<String> actual = Arrays.stream(InboxRepository.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("countUnread/3",
                "findById/1",
                "findByIdForRecipient/4",
                "listForRecipient/5",
                "markReadIfUnread/2"), actual);
    }
}
