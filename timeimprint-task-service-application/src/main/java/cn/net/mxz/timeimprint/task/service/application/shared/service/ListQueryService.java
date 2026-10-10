package cn.net.mxz.timeimprint.task.service.application.shared.service;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.action.model.ActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.shared.model.AttemptRecord;
import cn.net.mxz.timeimprint.task.service.application.signal.model.SignalRecord;
import cn.net.mxz.timeimprint.task.service.application.transition.model.TransitionRecord;
import cn.net.mxz.timeimprint.task.service.application.shared.paging.PageCursors;
import cn.net.mxz.timeimprint.task.service.application.action.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ParticipantQuery;
import cn.net.mxz.timeimprint.task.service.application.definition.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.instance.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.application.signal.port.TaskSignalRepository;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionQuery;
import cn.net.mxz.timeimprint.task.service.extension.shared.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.scenario.spi.ScenarioExtension;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** E01/E05/E08 list queries and I02–I05 diagnostics. */
@Service
public class ListQueryService {

    public record PageResult<T>(List<T> items, String nextCursor, boolean hasMore) {}

    public record ScenarioCatalogItem(
            String scenarioKey,
            String displayName,
            String contractVersion,
            List<Integer> supportedScenarioSchemaVersions,
            List<String> definitionCommandKeys,
            List<String> instanceCommandKeys,
            List<String> requiredCapabilities) {}

    private static final List<String> DEFINITION_COMMANDS =
            List.of("update", "pause", "resume", "retire");
    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "reminder", "通用提醒",
            "recurring_todo", "周期待办");

    private final ActorContextProvider actorContextProvider;
    private final ExtensionRegistry extensionRegistry;
    private final TaskDefinitionRepository definitionRepository;
    private final TaskInstanceRepository instanceRepository;
    private final ParticipantQuery participantQuery;
    private final TaskSignalRepository signalRepository;
    private final ActionJobExecutionPort actionJobExecutionPort;
    private final TransitionQuery transitionQuery;
    private final BusinessClock clock;

    public ListQueryService(
            ActorContextProvider actorContextProvider,
            ExtensionRegistry extensionRegistry,
            TaskDefinitionRepository definitionRepository,
            TaskInstanceRepository instanceRepository,
            ParticipantQuery participantQuery,
            TaskSignalRepository signalRepository,
            ActionJobExecutionPort actionJobExecutionPort,
            TransitionQuery transitionQuery,
            BusinessClock clock) {
        this.actorContextProvider = actorContextProvider;
        this.extensionRegistry = extensionRegistry;
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.participantQuery = participantQuery;
        this.signalRepository = signalRepository;
        this.actionJobExecutionPort = actionJobExecutionPort;
        this.transitionQuery = transitionQuery;
        this.clock = clock;
    }

    public Instant asOf() {
        return clock.nowUtcSeconds();
    }

    public PageResult<ScenarioCatalogItem> listScenarios(String cursor, int limit) {
        actorContextProvider.requireCurrentActor();
        int safeLimit = normalizeLimit(limit);
        List<ScenarioExtension> all = extensionRegistry.scenarioExtensions().listAll();
        int start = 0;
        if (cursor != null && !cursor.isBlank()) {
            start = -1;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).registrationKey().scenarioKey().equals(cursor)) {
                    start = i + 1;
                    break;
                }
            }
            if (start < 0) {
                throw new ApplicationException("INVALID_CURSOR", "cursor");
            }
        }
        List<ScenarioCatalogItem> page = new ArrayList<>();
        for (int i = start; i < all.size() && page.size() < safeLimit + 1; i++) {
            page.add(toCatalog(all.get(i)));
        }
        return slice(page, safeLimit, ScenarioCatalogItem::scenarioKey);
    }

    public PageResult<TaskDefinitionSnapshot> listDefinitions(
            String scenarioKey, String controlState, String participantRole, String cursor, int limit) {
        var actor = actorContextProvider.requireCurrentActor();
        int safeLimit = normalizeLimit(limit);
        String sk = blankToNull(scenarioKey);
        String cs = blankToNull(controlState);
        Instant cursorUpdatedAt = null;
        Long cursorDefinitionId = null;
        if (cursor != null && !cursor.isBlank()) {
            var decoded = PageCursors.decodeDefinition(cursor, actor.tenantKey(), sk, cs);
            cursorUpdatedAt = decoded.updatedAt();
            cursorDefinitionId = decoded.definitionId();
        }
        // Over-fetch when filtering by role in memory.
        int fetch = participantRole == null || participantRole.isBlank() ? safeLimit + 1 : Math.min(100, safeLimit * 5 + 1);
        List<TaskDefinitionSnapshot> rows = definitionRepository.list(
                actor.tenantKey(), actor.principalId(), sk, cs, cursorUpdatedAt, cursorDefinitionId, fetch);
        if (participantRole != null && !participantRole.isBlank()) {
            rows = rows.stream()
                    .filter(d -> participantQuery.listDefinitionLevel(d.definitionId()).stream()
                            .anyMatch(p -> participantRole.equals(p.roleCode())))
                    .toList();
        }
        List<TaskDefinitionSnapshot> page = rows.size() > safeLimit + 1
                ? rows.subList(0, safeLimit + 1)
                : rows;
        boolean hasMore = page.size() > safeLimit;
        List<TaskDefinitionSnapshot> items = hasMore ? page.subList(0, safeLimit) : page;
        String next = null;
        if (hasMore && !items.isEmpty()) {
            var last = items.get(items.size() - 1);
            next = PageCursors.encodeDefinition(new PageCursors.DefinitionCursor(
                    actor.tenantKey(), sk, cs, last.updatedAt(), last.definitionId()));
        }
        return new PageResult<>(List.copyOf(items), next, hasMore);
    }

    public PageResult<TaskInstanceSnapshot> listInstances(
            Long definitionId,
            String scenarioKey,
            String lifecycleCategory,
            String scenarioState,
            String participantRole,
            Instant from,
            Instant to,
            String cursor,
            int limit) {
        var actor = actorContextProvider.requireCurrentActor();
        int safeLimit = normalizeLimit(limit);
        String sk = blankToNull(scenarioKey);
        String lc = blankToNull(lifecycleCategory);
        String ss = blankToNull(scenarioState);
        Instant cursorOccurrenceAt = null;
        Long cursorInstanceId = null;
        if (cursor != null && !cursor.isBlank()) {
            var decoded = PageCursors.decodeInstance(cursor, actor.tenantKey(), definitionId, sk, lc, ss);
            cursorOccurrenceAt = decoded.occurrenceAt();
            cursorInstanceId = decoded.instanceId();
        }
        int fetch = participantRole == null || participantRole.isBlank() ? safeLimit + 1 : Math.min(100, safeLimit * 5 + 1);
        List<TaskInstanceSnapshot> rows = instanceRepository.list(
                actor.tenantKey(),
                definitionId,
                sk,
                lc,
                ss,
                from,
                to,
                cursorOccurrenceAt,
                cursorInstanceId,
                fetch);
        if (participantRole != null && !participantRole.isBlank()) {
            rows = rows.stream()
                    .filter(i -> participantQuery.listDefinitionLevel(i.definitionId()).stream()
                            .anyMatch(p -> participantRole.equals(p.roleCode())))
                    .toList();
        }
        List<TaskInstanceSnapshot> page = rows.size() > safeLimit + 1
                ? rows.subList(0, safeLimit + 1)
                : rows;
        boolean hasMore = page.size() > safeLimit;
        List<TaskInstanceSnapshot> items = hasMore ? page.subList(0, safeLimit) : page;
        String next = null;
        if (hasMore && !items.isEmpty()) {
            var last = items.get(items.size() - 1);
            next = PageCursors.encodeInstance(new PageCursors.InstanceCursor(
                    actor.tenantKey(),
                    definitionId,
                    sk,
                    lc,
                    ss,
                    last.occurrenceAt() != null ? last.occurrenceAt() : Instant.EPOCH,
                    last.instanceId()));
        }
        return new PageResult<>(List.copyOf(items), next, hasMore);
    }

    public SignalRecord getSignal(long signalId) {
        actorContextProvider.requireCurrentActor();
        return signalRepository
                .findById(signalId)
                .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "signal"));
    }

    public PageResult<ActionJobRecord> listActionJobs(
            Long definitionId, Long instanceId, String status, String handlerKey, String cursor, int limit) {
        actorContextProvider.requireCurrentActor();
        int safeLimit = normalizeLimit(limit);
        List<ActionJobRecord> rows = actionJobExecutionPort.listFiltered(
                definitionId, instanceId, blankToNull(status), blankToNull(handlerKey), safeLimit + 1, cursor);
        return slice(rows, safeLimit, a -> String.valueOf(a.actionJobId()));
    }

    public ActionJobRecord getActionJob(long actionJobId) {
        actorContextProvider.requireCurrentActor();
        return actionJobExecutionPort
                .findById(actionJobId)
                .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "actionJob"));
    }

    public List<AttemptRecord> listAttempts(long actionJobId) {
        return actionJobExecutionPort.listAttempts(actionJobId);
    }

    public PageResult<TransitionRecord> listTransitions(
            Long definitionId, Long instanceId, String cursor, int limit) {
        actorContextProvider.requireCurrentActor();
        if (definitionId == null && instanceId == null) {
            throw new ApplicationException("INVALID_REQUEST", "definitionId or instanceId required");
        }
        int safeLimit = normalizeLimit(limit);
        List<TransitionRecord> rows =
                transitionQuery.list(definitionId, instanceId, cursor, safeLimit + 1);
        return slice(rows, safeLimit, t -> String.valueOf(t.transitionId()));
    }

    private ScenarioCatalogItem toCatalog(ScenarioExtension ext) {
        var d = ext.descriptor();
        String key = d.scenarioKey();
        List<String> caps = new ArrayList<>(d.requiredCapabilityKeys());
        caps.sort(Comparator.naturalOrder());
        List<String> instanceCmds = new ArrayList<>(d.declaredInstanceCommandKeys());
        instanceCmds.sort(Comparator.naturalOrder());
        return new ScenarioCatalogItem(
                key,
                DISPLAY_NAMES.getOrDefault(key, key),
                String.valueOf(d.contractVersion()),
                List.of(1),
                DEFINITION_COMMANDS,
                instanceCmds,
                caps);
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private static <T> PageResult<T> slice(List<T> rows, int limit, java.util.function.Function<T, String> idFn) {
        boolean hasMore = rows.size() > limit;
        List<T> items = hasMore ? rows.subList(0, limit) : rows;
        String next = hasMore && !items.isEmpty() ? idFn.apply(items.get(items.size() - 1)) : null;
        return new PageResult<>(List.copyOf(items), next, hasMore);
    }
}
