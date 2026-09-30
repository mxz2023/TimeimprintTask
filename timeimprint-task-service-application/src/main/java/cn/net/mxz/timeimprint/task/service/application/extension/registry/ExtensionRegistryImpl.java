package cn.net.mxz.timeimprint.task.service.application.extension.registry;

import cn.net.mxz.timeimprint.task.service.extension.action.registry.ActionHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.action.registry.ActionHandlerRegistry;
import cn.net.mxz.timeimprint.task.service.extension.shared.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.policy.registry.PolicyRegistrationKey;
import cn.net.mxz.timeimprint.task.service.extension.policy.registry.PolicyRegistry;
import cn.net.mxz.timeimprint.task.service.extension.materialization.spi.ScenarioDataMaterializerKey;
import cn.net.mxz.timeimprint.task.service.extension.materialization.registry.ScenarioDataMaterializerRegistry;
import cn.net.mxz.timeimprint.task.service.extension.scenario.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.scenario.registry.ScenarioExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.command.registry.TaskCommandHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.command.registry.TaskCommandHandlerRegistry;
import cn.net.mxz.timeimprint.task.service.extension.trigger.registry.TriggerProviderKey;
import cn.net.mxz.timeimprint.task.service.extension.trigger.registry.TriggerProviderRegistry;
import cn.net.mxz.timeimprint.task.service.extension.action.spi.ActionHandler;
import cn.net.mxz.timeimprint.task.service.extension.policy.spi.Policy;
import cn.net.mxz.timeimprint.task.service.extension.materialization.spi.ScenarioDataMaterializer;
import cn.net.mxz.timeimprint.task.service.extension.scenario.spi.ScenarioExtension;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.TaskCommandHandler;
import cn.net.mxz.timeimprint.task.service.extension.trigger.spi.TriggerProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import cn.net.mxz.timeimprint.task.service.extension.policy.spi.PolicyPhase;

/** Auto-wires all registered extensions from the Spring context. */
@Component
public class ExtensionRegistryImpl implements ExtensionRegistry {

    private final ScenarioExtensionRegistry scenarioExtensions;
    private final TriggerProviderRegistry triggerProviders;
    private final TaskCommandHandlerRegistry commandHandlers;
    private final ActionHandlerRegistry actionHandlers;
    private final PolicyRegistry policies;
    private final ScenarioDataMaterializerRegistry materializerRegistry;

    public ExtensionRegistryImpl(
            List<ScenarioExtension> scenarioExtList,
            List<TriggerProvider> triggerProviderList,
            List<TaskCommandHandler> commandHandlerList,
            List<ActionHandler> actionHandlerList,
            List<Policy> policyList,
            List<ScenarioDataMaterializer> materializerList) {
        scenarioExtensions = new MapScenarioRegistry(toMap(scenarioExtList, ScenarioExtension::registrationKey));
        triggerProviders = new MapTriggerRegistry(toMap(triggerProviderList, TriggerProvider::registrationKey));
        commandHandlers = new MapCommandHandlerRegistry(toMap(commandHandlerList, TaskCommandHandler::registrationKey));
        actionHandlers = new MapActionHandlerRegistry(toMap(actionHandlerList, ActionHandler::registrationKey));
        policies = new MapPolicyRegistry(toMap(policyList, Policy::registrationKey));
        materializerRegistry = new MapMaterializerRegistry(toMap(materializerList, ScenarioDataMaterializer::registrationKey));
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
        @Override public List<Policy> policiesForPhase(cn.net.mxz.timeimprint.task.service.extension.policy.spi.PolicyPhase phase) {
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
