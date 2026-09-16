package cn.net.mxz.timeimprint.task.service.application.definition.service;

import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;

/** Shared idempotency hash check for definition commands. */
final class DefinitionCommandDedup {
    private DefinitionCommandDedup() {}

    static void assertSameRequestHash(byte[] stored, byte[] incoming) {
        if (stored != null && !java.util.Arrays.equals(stored, incoming)) {
            throw new ApplicationException("IDEMPOTENCY_CONFLICT", "same requestId different payload");
        }
    }
}
