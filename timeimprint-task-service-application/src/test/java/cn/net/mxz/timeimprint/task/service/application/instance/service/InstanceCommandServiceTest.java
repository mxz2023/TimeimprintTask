package cn.net.mxz.timeimprint.task.service.application.instance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.net.mxz.timeimprint.task.service.application.access.model.ActorContext;
import cn.net.mxz.timeimprint.task.service.application.action.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.definition.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.instance.port.InstanceCommandPort;
import cn.net.mxz.timeimprint.task.service.application.instance.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.shared.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionCommitRequest;
import cn.net.mxz.timeimprint.task.service.extension.command.context.CommandExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.CommandScope;
import cn.net.mxz.timeimprint.task.service.extension.command.registry.TaskCommandHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.command.registry.TaskCommandHandlerRegistry;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.TaskCommandHandler;
import cn.net.mxz.timeimprint.task.service.extension.shared.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Owner test for InstanceCommandService: public surface and instance-command lock order. */
class InstanceCommandServiceTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(InstanceCommandService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("execute/6"), actual);
        assertFalse(actual.isEmpty());
    }

    @Test
    void executeLocksDefinitionBeforeInstanceAndUsesLockedRevision() {
        List<String> events = new ArrayList<>();
        TaskInstanceSnapshot plain = instance(1L);
        TaskInstanceSnapshot locked = instance(9L);
        InstanceCommandService service = service(events, plain, locked, new HandlerResult.NoChange("ok"));

        ApplicationException conflict = assertThrows(
                ApplicationException.class,
                () -> service.execute(7L, "complete", 1, "req-1", 1L, "{}"));
        assertEquals("REVISION_CONFLICT", conflict.errorCode());
        assertEquals("current=9", conflict.getMessage().substring(conflict.getMessage().lastIndexOf("current=")));
        assertEquals(List.of("read-instance", "lock-definition:9", "lock-instance"), events);
    }

    @Test
    void executeReachesHandlerOnlyAfterDefinitionAndInstanceLocks() {
        List<String> events = new ArrayList<>();
        TaskInstanceSnapshot snapshot = instance(4L);
        InstanceCommandService service = service(events, snapshot, snapshot, new HandlerResult.NoChange("ok"));

        InstanceCommandService.InstanceCommandResult result =
                service.execute(7L, "complete", 1, "req-2", 4L, "{}");
        assertEquals(4L, result.newRevision());
        assertFalse(result.changed());
        assertEquals(List.of("read-instance", "lock-definition:9", "lock-instance", "read-instance"), events);
    }

    private static InstanceCommandService service(
            List<String> events,
            TaskInstanceSnapshot plain,
            TaskInstanceSnapshot locked,
            HandlerResult handlerResult) {
        TaskInstanceRepository instances = new TaskInstanceRepository() {
            @Override
            public Optional<TaskInstanceSnapshot> findById(long instanceId) {
                events.add("read-instance");
                return Optional.of(plain);
            }

            @Override
            public Optional<TaskInstanceSnapshot> findByIdForUpdate(long instanceId) {
                events.add("lock-instance");
                return Optional.of(locked);
            }

            @Override
            public List<TaskInstanceSnapshot> listByDefinition(long definitionId) {
                return List.of();
            }

            @Override
            public List<TaskInstanceSnapshot> list(
                    String tenantId, Long definitionId, String scenarioKey, String lifecycleCategory,
                    String scenarioState, Instant from, Instant to, Instant cursorOccurrenceAt,
                    Long cursorInstanceId, int limit) {
                return List.of();
            }
        };
        TaskDefinitionRepository definitions = new TaskDefinitionRepository() {
            @Override
            public Optional<TaskDefinitionSnapshot> findById(long definitionId) {
                return Optional.empty();
            }

            @Override
            public Optional<TaskDefinitionSnapshot> findByIdForUpdate(long definitionId) {
                events.add("lock-definition:" + definitionId);
                Instant now = Instant.parse("2026-10-02T01:00:00Z");
                return Optional.of(new TaskDefinitionSnapshot(
                        definitionId, "local", "recurring_todo", 1, "提交周报", "整理本周工作", "{}",
                        ControlState.ACTIVE, 1L, 1L, now, now));
            }

            @Override
            public List<TaskDefinitionSnapshot> list(
                    String tenantId, String scenarioKey, String controlState, Instant cursorUpdatedAt,
                    Long cursorDefinitionId, int limit) {
                return List.of();
            }
        };
        CommandDedupRepository dedup = new CommandDedupRepository() {
            @Override
            public Optional<String> findCompletedResponseJson(
                    String tenantId, String actorId, String operation, String requestId) {
                return Optional.empty();
            }

            @Override
            public Optional<CommandDedupSnapshot> find(
                    String tenantId, String actorId, String operation, String requestId) {
                return Optional.empty();
            }

            @Override
            public boolean tryBegin(
                    String tenantId, String actorId, String operation, String requestId, byte[] requestHash) {
                return true;
            }

            @Override
            public void complete(
                    String tenantId, String actorId, String operation, String requestId, String resultCode,
                    String resourceType, String resourceId, Long resourceRevision, String responseJson) {
            }
        };
        TaskCommandHandler handler = new TaskCommandHandler() {
            @Override
            public TaskCommandHandlerKey registrationKey() {
                return new TaskCommandHandlerKey("recurring_todo", CommandScope.INSTANCE, "complete", 1);
            }

            @Override
            public HandlerResult handle(CommandExecutionContext context) {
                return handlerResult;
            }
        };
        ExtensionRegistry registry = new ExtensionRegistry() {
            @Override
            public cn.net.mxz.timeimprint.task.service.extension.scenario.registry.ScenarioExtensionRegistry
                    scenarioExtensions() {
                throw new UnsupportedOperationException();
            }

            @Override
            public cn.net.mxz.timeimprint.task.service.extension.trigger.registry.TriggerProviderRegistry
                    triggerProviders() {
                throw new UnsupportedOperationException();
            }

            @Override
            public TaskCommandHandlerRegistry commandHandlers() {
                return new TaskCommandHandlerRegistry() {
                    @Override
                    public Optional<TaskCommandHandler> find(TaskCommandHandlerKey key) {
                        return Optional.of(handler);
                    }

                    @Override
                    public TaskCommandHandler require(TaskCommandHandlerKey key) {
                        return handler;
                    }
                };
            }

            @Override
            public cn.net.mxz.timeimprint.task.service.extension.action.registry.ActionHandlerRegistry
                    actionHandlers() {
                throw new UnsupportedOperationException();
            }

            @Override
            public cn.net.mxz.timeimprint.task.service.extension.policy.registry.PolicyRegistry policies() {
                throw new UnsupportedOperationException();
            }

            @Override
            public cn.net.mxz.timeimprint.task.service.extension.materialization.registry
                    .ScenarioDataMaterializerRegistry scenarioDataMaterializers() {
                throw new UnsupportedOperationException();
            }
        };
        ActionJobExecutionPort actions = proxy(ActionJobExecutionPort.class, (method, args) -> {
            if ("listByInstance".equals(method.getName())) {
                return List.of();
            }
            return defaultValue(method.getReturnType());
        });
        ActorContextProvider actors = new ActorContextProvider() {
            @Override
            public Optional<ActorContext> currentActor() {
                return Optional.of(new ActorContext("USER", "local-actor", "local-tenant"));
            }

            @Override
            public ActorContext requireCurrentActor() {
                return currentActor().orElseThrow();
            }
        };
        return new InstanceCommandService(
                actors,
                definitions,
                instances,
                dedup,
                registry,
                (TransitionCommitRequest request) -> {
                    throw new AssertionError("NoChange must not commit");
                },
                (InstanceCommandPort) proxy(InstanceCommandPort.class, (method, args) -> null),
                actions,
                new TransactionBoundary() {
                    @Override
                    public <T> T execute(Supplier<T> work) {
                        return work.get();
                    }

                    @Override
                    public void execute(Runnable work) {
                        work.run();
                    }
                },
                () -> Instant.parse("2026-10-02T01:00:00Z"),
                JsonMapper.builder().build());
    }

    private static TaskInstanceSnapshot instance(long revision) {
        Instant now = Instant.parse("2026-10-02T01:00:00Z");
        return new TaskInstanceSnapshot(
                7L, 9L, 1L, 1L, 1L, "occ", now, now,
                LifecycleCategory.ACTIVE, "PENDING", 1, "{}",
                "提交周报", "整理本周工作", revision, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (unused, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> type.getSimpleName();
                            case "hashCode" -> System.identityHashCode(unused);
                            case "equals" -> unused == args[0];
                            default -> null;
                        };
                    }
                    return call.invoke(method, args);
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type.equals(boolean.class)) {
            return false;
        }
        if (type.equals(int.class)) {
            return 0;
        }
        if (type.equals(long.class)) {
            return 0L;
        }
        if (List.class.isAssignableFrom(type)) {
            return List.of();
        }
        if (Optional.class.isAssignableFrom(type)) {
            return Optional.empty();
        }
        return null;
    }

    @FunctionalInterface
    private interface Call {
        Object invoke(java.lang.reflect.Method method, Object[] args);
    }
}
