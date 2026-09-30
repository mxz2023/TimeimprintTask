package cn.net.mxz.timeimprint.task.service.application.definition.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import org.junit.jupiter.api.Test;

/** Owner test for DefinitionCommandDedup. */
class DefinitionCommandDedupTest {

    @Test
    void rejectsMismatchedRequestHash() {
        assertDoesNotThrow(() -> DefinitionCommandDedup.assertSameRequestHash(new byte[] {1}, new byte[] {1}));
        ApplicationException ex = assertThrows(
                ApplicationException.class,
                () -> DefinitionCommandDedup.assertSameRequestHash(new byte[] {1}, new byte[] {2}));
        assertEquals("IDEMPOTENCY_CONFLICT", ex.errorCode());
    }
}
