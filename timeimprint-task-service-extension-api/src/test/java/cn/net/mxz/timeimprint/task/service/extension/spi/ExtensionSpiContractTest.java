package cn.net.mxz.timeimprint.task.service.extension.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.service.extension.context.CommandExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.context.SignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.policy.PolicyPhase;
import cn.net.mxz.timeimprint.task.service.extension.registry.ActionHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.registry.PolicyRegistrationKey;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioDataMaterializerKey;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.registry.TaskCommandHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.registry.TriggerProviderKey;
import cn.net.mxz.timeimprint.task.service.extension.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionPlan;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ExtensionSpiContractTest {

    @Test
    void handlerResultSealedVariantsExist() {
        assertTrue(HandlerResult.class.isSealed());
        assertEquals(3, HandlerResult.class.getPermittedSubclasses().length);
        assertInstanceOf(
                HandlerResult.class,
                new HandlerResult.Applied(new TransitionPlan(
                        new cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionTarget(
                                cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionResourceType
                                        .DEFINITION,
                                1L,
                                1L),
                        null,
                        null,
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        "")));
        assertInstanceOf(HandlerResult.class, new HandlerResult.NoChange("snapshot"));
        assertInstanceOf(HandlerResult.class, new HandlerResult.Rejected("STATE_CONFLICT", "safe"));
    }

    @Test
    void registrationKeysAreRecordsWithExpectedComponents() {
        assertRecordComponents(ScenarioExtensionKey.class, "scenarioKey", "contractVersion");
        assertRecordComponents(TriggerProviderKey.class, "providerKey", "contractVersion");
        assertRecordComponents(
                TaskCommandHandlerKey.class, "scenarioKey", "scope", "commandKey", "commandSchemaVersion");
        assertRecordComponents(ActionHandlerKey.class, "handlerKey", "actionSchemaVersion");
        assertRecordComponents(PolicyRegistrationKey.class, "policyKey", "phase");
        assertRecordComponents(ScenarioDataMaterializerKey.class, "scenarioKey", "mutationKey", "schemaVersion");
    }

    @Test
    void scenarioExtensionSignalEntryReturnsHandlerResult() throws NoSuchMethodException {
        Method method = ScenarioExtension.class.getMethod("processSignal", SignalProcessContext.class);
        assertEquals(HandlerResult.class, method.getReturnType());
        assertTrue(Modifier.isAbstract(ScenarioExtension.class.getModifiers()));
    }

    @Test
    void taskCommandHandlerReturnsHandlerResult() throws NoSuchMethodException {
        Method method = TaskCommandHandler.class.getMethod("handle", CommandExecutionContext.class);
        assertEquals(HandlerResult.class, method.getReturnType());
    }

    @Test
    void policyCoversFivePhases() {
        assertEquals(5, PolicyPhase.values().length);
    }

    private static void assertRecordComponents(Class<?> type, String... names) {
        assertTrue(type.isRecord());
        RecordComponent[] components = type.getRecordComponents();
        assertEquals(names.length, components.length);
        assertEquals(Arrays.asList(names), Arrays.stream(components).map(RecordComponent::getName).toList());
    }
}
