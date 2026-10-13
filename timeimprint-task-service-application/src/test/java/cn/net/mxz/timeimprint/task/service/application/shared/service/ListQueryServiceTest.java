package cn.net.mxz.timeimprint.task.service.application.shared.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.service.application.access.model.ActorContext;
import cn.net.mxz.timeimprint.task.service.application.instance.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ListQueryService public method surface for P02 refactor safety. */
class ListQueryServiceTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(ListQueryService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("asOf/0",
                "getActionJob/1",
                "getSignal/1",
                "listActionJobs/6",
                "listAttempts/1",
                "listDefinitions/5",
                "listInstances/9",
                "listScenarios/2",
                "listTransitions/4"), actual);
        assertFalse(actual.isEmpty());
    }

    @Test
    void listInstancesUsesOnlyTheCurrentParticipant() {
        AtomicReference<String> principal = new AtomicReference<>();
        TaskInstanceRepository instances = new TaskInstanceRepository() {
            @Override
            public Optional<TaskInstanceSnapshot> findById(long instanceId) {
                return Optional.empty();
            }

            @Override
            public Optional<TaskInstanceSnapshot> findByIdForUpdate(long instanceId) {
                return Optional.empty();
            }

            @Override
            public List<TaskInstanceSnapshot> listByDefinition(long definitionId) {
                return List.of();
            }

            @Override
            public List<TaskInstanceSnapshot> list(
                    String tenantId,
                    String participantPrincipalId,
                    Long definitionId,
                    String scenarioKey,
                    String lifecycleCategory,
                    String scenarioState,
                    Instant from,
                    Instant to,
                    Instant cursorOccurrenceAt,
                    Long cursorInstanceId,
                    int limit) {
                principal.set(participantPrincipalId);
                return List.of();
            }
        };
        ActorContext actor = new ActorContext("USER", "u-current", "local-tenant");
        ActorContextProvider actors = new ActorContextProvider() {
            @Override
            public Optional<ActorContext> currentActor() {
                return Optional.of(actor);
            }

            @Override
            public ActorContext requireCurrentActor() {
                return actor;
            }
        };
        ListQueryService service = new ListQueryService(
                actors,
                null,
                null,
                instances,
                null,
                null,
                null,
                null,
                null);
        var page = service.listInstances(null, null, null, null, null, null, null, null, 20);
        assertEquals("u-current", principal.get());
        assertTrue(page.items().isEmpty());
        assertFalse(page.hasMore());
    }
}
