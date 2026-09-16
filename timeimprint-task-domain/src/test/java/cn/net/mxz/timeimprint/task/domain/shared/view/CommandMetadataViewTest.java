package cn.net.mxz.timeimprint.task.domain.shared.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze CommandMetadataView record components for P02 refactor safety. */
class CommandMetadataViewTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(CommandMetadataView.class.isRecord());
        List<String> actual = Arrays.stream(CommandMetadataView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("commandKey",
                "supportedCommandSchemaVersions"), actual);
    }
}
