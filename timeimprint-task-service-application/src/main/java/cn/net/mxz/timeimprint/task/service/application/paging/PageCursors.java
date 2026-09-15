package cn.net.mxz.timeimprint.task.service.application.paging;

import cn.net.mxz.timeimprint.task.service.application.exception.ApplicationException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Opaque base64url keyset cursors (04): freeze sort-boundary values at page time so concurrent
 * updates cannot rewind the walk into duplicate rows.
 */
public final class PageCursors {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private PageCursors() {}

    public record DefinitionCursor(
            String tenantId,
            String scenarioKey,
            String controlState,
            Instant updatedAt,
            long definitionId) {}

    public record InstanceCursor(
            String tenantId,
            Long definitionId,
            String scenarioKey,
            String lifecycleCategory,
            String scenarioState,
            Instant occurrenceAt,
            long instanceId) {}

    public static String encodeDefinition(DefinitionCursor c) {
        String raw = String.join(
                "\n",
                "def",
                nullToEmpty(c.tenantId()),
                nullToEmpty(c.scenarioKey()),
                nullToEmpty(c.controlState()),
                c.updatedAt().getEpochSecond() + "",
                Long.toString(c.definitionId()));
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static DefinitionCursor decodeDefinition(
            String cursor, String tenantId, String scenarioKey, String controlState) {
        String[] parts = decodeParts(cursor, 6);
        if (!"def".equals(parts[0])) {
            throw invalid();
        }
        if (!nullToEmpty(tenantId).equals(parts[1])
                || !nullToEmpty(scenarioKey).equals(parts[2])
                || !nullToEmpty(controlState).equals(parts[3])) {
            throw invalid();
        }
        try {
            Instant updatedAt = Instant.ofEpochSecond(Long.parseLong(parts[4]));
            long definitionId = Long.parseLong(parts[5]);
            return new DefinitionCursor(tenantId, scenarioKey, controlState, updatedAt, definitionId);
        } catch (RuntimeException ex) {
            throw invalid();
        }
    }

    public static String encodeInstance(InstanceCursor c) {
        String raw = String.join(
                "\n",
                "inst",
                nullToEmpty(c.tenantId()),
                c.definitionId() == null ? "" : Long.toString(c.definitionId()),
                nullToEmpty(c.scenarioKey()),
                nullToEmpty(c.lifecycleCategory()),
                nullToEmpty(c.scenarioState()),
                c.occurrenceAt().getEpochSecond() + "",
                Long.toString(c.instanceId()));
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static InstanceCursor decodeInstance(
            String cursor,
            String tenantId,
            Long definitionId,
            String scenarioKey,
            String lifecycleCategory,
            String scenarioState) {
        String[] parts = decodeParts(cursor, 8);
        if (!"inst".equals(parts[0])) {
            throw invalid();
        }
        String expectedDef = definitionId == null ? "" : Long.toString(definitionId);
        if (!nullToEmpty(tenantId).equals(parts[1])
                || !expectedDef.equals(parts[2])
                || !nullToEmpty(scenarioKey).equals(parts[3])
                || !nullToEmpty(lifecycleCategory).equals(parts[4])
                || !nullToEmpty(scenarioState).equals(parts[5])) {
            throw invalid();
        }
        try {
            Instant occurrenceAt = Instant.ofEpochSecond(Long.parseLong(parts[6]));
            long instanceId = Long.parseLong(parts[7]);
            return new InstanceCursor(
                    tenantId, definitionId, scenarioKey, lifecycleCategory, scenarioState, occurrenceAt, instanceId);
        } catch (RuntimeException ex) {
            throw invalid();
        }
    }

    private static String[] decodeParts(String cursor, int expected) {
        if (cursor == null || cursor.isBlank()) {
            throw invalid();
        }
        try {
            String raw = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\n", -1);
            if (parts.length != expected) {
                throw invalid();
            }
            return parts;
        } catch (IllegalArgumentException ex) {
            throw invalid();
        }
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static ApplicationException invalid() {
        return new ApplicationException("INVALID_CURSOR", "cursor");
    }
}
