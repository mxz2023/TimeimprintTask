package cn.net.mxz.timeimprint.task.service.storage.mysql.inbox.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze InboxRepositoryImpl public method surface for P02 refactor safety. */
class InboxRepositoryImplTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(InboxRepositoryImpl.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
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
        assertFalse(actual.isEmpty());
    }
}
