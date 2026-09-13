package cn.net.mxz.timeimprint.task.service.application.registry;

import cn.net.mxz.timeimprint.task.service.extension.registry.ActionHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.registry.ActionHandlerRegistry;
import cn.net.mxz.timeimprint.task.service.extension.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.registry.PolicyRegistrationKey;
import cn.net.mxz.timeimprint.task.service.extension.registry.PolicyRegistry;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioDataMaterializerKey;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioDataMaterializerRegistry;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.registry.TaskCommandHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.registry.TaskCommandHandlerRegistry;
import cn.net.mxz.timeimprint.task.service.extension.registry.TriggerProviderKey;
import cn.net.mxz.timeimprint.task.service.extension.registry.TriggerProviderRegistry;
import cn.net.mxz.timeimprint.task.service.extension.spi.ActionHandler;
import cn.net.mxz.timeimprint.task.service.extension.spi.Policy;
import cn.net.mxz.timeimprint.task.service.extension.spi.ScenarioDataMaterializer;
import cn.net.mxz.timeimprint.task.service.extension.spi.ScenarioExtension;
import cn.net.mxz.timeimprint.task.service.extension.spi.TaskCommandHandler;
import cn.net.mxz.timeimprint.task.service.extension.spi.TriggerProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Auto-wires all registered extensions from the Spring context. */
@Component
public class MxzExtensionRegistryImpl implements ExtensionRegistry {

    private final ScenarioExtensionRegistry scenarioExtensions;
    private final TriggerProviderRegistry triggerProviders;
    private final TaskCommandHandlerRegistry commandHandlers;
    private final ActionHandlerRegistry actionHandlers;
    private final PolicyRegistry policies;
    private final ScenarioDataMaterializerRegistry materializerRegistry;

    public MxzExtensionRegistryImpl(
            List<ScenarioExtension> scenarioExtList,
            List<TriggerProvider> triggerProviderList,
            List<TaskCommandHandler> commandHandlerList,
            List<ActionHandler> actionHandlerList,
            List<Policy> policyList,
            List<ScenarioDataMaterializer> materializerList) {
        scenarioExtensions = new MapScenarioRegistry(toMap(scenarioExtList, e -> e.registrationKey()));
        triggerProviders = new MapTriggerRegistry(toMap(triggerProviderList, e -> e.registrationKey()));
        commandHandlers = new MapCommandHandlerRegistry(toMap(commandHandlerList, e -> e.registrationKey()));
        actionHandlers = new MapActionHandlerRegistry(toMap(actionHandlerList, e -> e.registrationKey()));
        policies = new MapPolicyRegistry(toMap(policyList, e -> e.registrationKey()));
        materializerRegistry = new MapMaterializerRegistry(toMap(materializerList, e -> e.registrationKey()));
    }

    private <K, V> Map<K, V> toMap(List<V> list, Function<V, K> keyFn) {
        return list.stream().collect(Collectors.toMap(keyFn, v -> v));
    }

    @Override public ScenarioExtensionRegistry scenarioExtensions() { return scenarioExtensions; }
    @Override public TriggerProviderRegistry triggerProviders() { return triggerProviders; }
    @Override public TaskCommandHandlerRegistry commandHandlers() { return commandHandlers; }
    @Override public ActionHandlerRegistry actionHandlers() { return actionHandlers; }
    @Override public PolicyRegistry policies() { return policies; }
    @Override public ScenarioDataMaterializerRegistry scenarioDataMaterializers() { return materializerRegistry; }

    // ------- Inner map-backed implementations -------

    record MapScenarioRegistry(Map<ScenarioExtensionKey, ScenarioExtension> map) implements ScenarioExtensionRegistry {
        @Override public Optional<ScenarioExtension> find(ScenarioExtensionKey k) { return Optional.ofNullable(map.get(k)); }
        @Override public ScenarioExtension require(ScenarioExtensionKey k) { return find(k).orElseThrow(() -> new IllegalStateException("ScenarioExtension not found: " + k)); }
        @Override public List<ScenarioExtension> listAll() {
            return map.values().stream()
                    .sorted(java.util.Comparator.comparing(e -> e.registrationKey().scenarioKey()))
                    .toList();
        }
    }
    record MapTriggerRegistry(Map<TriggerProviderKey, TriggerProvider> map) implements TriggerProviderRegistry {
        @Override public Optional<TriggerProvider> find(TriggerProviderKey k) { return Optional.ofNullable(map.get(k)); }
        @Override public TriggerProvider require(TriggerProviderKey k) { return find(k).orElseThrow(() -> new IllegalStateException("TriggerProvider not found: " + k)); }
    }
    record MapCommandHandlerRegistry(Map<TaskCommandHandlerKey, TaskCommandHandler> map) implements TaskCommandHandlerRegistry {
        @Override public Optional<TaskCommandHandler> find(TaskCommandHandlerKey k) { return Optional.ofNullable(map.get(k)); }
        @Override public TaskCommandHandler require(TaskCommandHandlerKey k) { return find(k).orElseThrow(() -> new IllegalStateException("TaskCommandHandler not found: " + k)); }
    }
    record MapActionHandlerRegistry(Map<ActionHandlerKey, ActionHandler> map) implements ActionHandlerRegistry {
        @Override public Optional<ActionHandler> find(ActionHandlerKey k) { return Optional.ofNullable(map.get(k)); }
        @Override public ActionHandler require(ActionHandlerKey k) { return find(k).orElseThrow(() -> new IllegalStateException("ActionHandler not found: " + k)); }
    }
    record MapPolicyRegistry(Map<PolicyRegistrationKey, Policy> map) implements PolicyRegistry {
        @Override public List<Policy> policiesForPhase(cn.net.mxz.timeimprint.task.service.extension.policy.PolicyPhase phase) {
            return map.values().stream()
                    .filter(p -> p.registrationKey().phase() == phase)
                    .sorted(java.util.Comparator.comparingInt(Policy::order))
                    .toList();
        }
    }
    record MapMaterializerRegistry(Map<ScenarioDataMaterializerKey, ScenarioDataMaterializer> map)
            implements ScenarioDataMaterializerRegistry {
        @Override public Optional<ScenarioDataMaterializer> find(ScenarioDataMaterializerKey k) { return Optional.ofNullable(map.get(k)); }
        @Override public ScenarioDataMaterializer require(ScenarioDataMaterializerKey k) { return find(k).orElseThrow(() -> new IllegalStateException("ScenarioDataMaterializer not found: " + k)); }
    }
}
