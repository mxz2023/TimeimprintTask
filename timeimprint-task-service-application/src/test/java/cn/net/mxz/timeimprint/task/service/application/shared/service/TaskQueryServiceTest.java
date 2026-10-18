package cn.net.mxz.timeimprint.task.service.application.shared.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.net.mxz.timeimprint.task.service.application.access.model.ActorContext;
import cn.net.mxz.timeimprint.task.service.application.action.model.ActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.action.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.definition.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.inbox.port.InboxRepository;
import cn.net.mxz.timeimprint.task.service.application.instance.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ParticipantRecord;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ParticipantQuery;
import cn.net.mxz.timeimprint.task.service.application.shared.port.TriggerBindingQuery;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TaskQueryService public method surface for P02 refactor safety. */
class TaskQueryServiceTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(TaskQueryService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("getDefinition/1",
                "getInstance/1",
                "listInstances/1"), actual);
        assertFalse(actual.isEmpty());
    }

    @Test
    void inboxCountStaysOnSucceededActionsWhileUnreadExcludesDeleted() {
        Instant now = Instant.parse("2026-10-18T10:54:00Z");
        TaskDefinitionRepository definitions = mock(TaskDefinitionRepository.class);
        TaskInstanceRepository instances = mock(TaskInstanceRepository.class);
        ParticipantQuery participants = mock(ParticipantQuery.class);
        ActionJobExecutionPort actions = mock(ActionJobExecutionPort.class);
        InboxRepository inbox = mock(InboxRepository.class);
        when(definitions.findById(3L)).thenReturn(Optional.of(new TaskDefinitionSnapshot(
                3L, "tenant", "reminder", 1, "title", "", "{}",
                ControlState.ACTIVE, 1L, 1L, now, now)));
        when(instances.findById(4L)).thenReturn(Optional.of(new TaskInstanceSnapshot(
                4L, 3L, null, null, 1L, null, now, now,
                LifecycleCategory.WAITING, "PLANNED", 1, "{}", "title", "", 1L, null)));
        when(participants.listDefinitionLevel(3L)).thenReturn(List.of(
                new ParticipantRecord(1L, 3L, null, "USER", "actor", "OWNER", "REQUEST")));
        when(actions.listByInstance(4L)).thenReturn(List.of(new ActionJobRecord(
                9L, "tenant", 3L, 4L, 5L, 1L,
                "in_app_notification", "key", "LOCAL_TRANSACTIONAL", 1,
                "USER", "actor", "{}", now, null, "SUCCEEDED",
                1, 3, now, null, null, "OK", null, now, null, 0)));
        when(inbox.countUnreadByInstance(4L)).thenReturn(0);
        ActorContextProvider actors = new ActorContextProvider() {
            @Override
            public Optional<ActorContext> currentActor() {
                return Optional.of(new ActorContext("USER", "actor", "tenant"));
            }

            @Override
            public ActorContext requireCurrentActor() {
                return currentActor().orElseThrow();
            }
        };
        TaskQueryService service = new TaskQueryService(
                definitions, instances, participants, mock(TriggerBindingQuery.class), actions, inbox, actors);
        TaskQueryService.InstanceDetail detail = service.getInstance(4L);
        assertEquals(1, detail.inboxCount());
        assertEquals(0, detail.unreadInboxCount());
    }
}
