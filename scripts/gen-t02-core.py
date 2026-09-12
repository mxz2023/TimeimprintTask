#!/usr/bin/env python3
"""Generate T02 vertical-slice core sources for TimeImprintTask."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BP = "cn.net.mxz.timeimprint.task"


def w(rel: str, content: str) -> None:
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.strip() + "\n", encoding="utf-8")
    print("wrote", rel)


# --- payloads ---
w(
    f"timeimprint-task-common/src/main/java/{BP.replace('.', '/')}/common/MxzJsonPayload.java",
    f"""
package {BP}.common;

import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload;
import java.util.Map;

/** 通用已校验 JSON 对象载荷（UTF-8 规范化后可哈希）。 */
public record MxzJsonPayload(Map<String, Object> fields) implements ScenarioMutationPayload {{
    public MxzJsonPayload {{
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }}
}}
""",
)

# Need common to depend on kernel for ScenarioMutationPayload - BAD dependency direction!
# ScenarioMutationPayload is in kernel. common cannot depend on kernel.
# Put MxzJsonPayload in kernel or extension-api instead.
)

# Fix: put in kernel
w(
    f"timeimprint-task-service-kernel/src/main/java/{BP.replace('.', '/')}/service/kernel/domain/mutation/MxzJsonPayload.java",
    f"""
package {BP}.service.kernel.domain.mutation;

import java.util.Map;

/** 通用已校验 JSON 对象载荷。 */
public record MxzJsonPayload(Map<String, Object> fields) implements ScenarioMutationPayload {{
    public MxzJsonPayload {{
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }}
}}
""",
)

# Expand snapshots
w(
    f"timeimprint-task-service-kernel/src/main/java/{BP.replace('.', '/')}/service/kernel/domain/snapshot/MxzTaskDefinitionSnapshot.java",
    f"""
package {BP}.service.kernel.domain.snapshot;

import {BP}.service.kernel.domain.state.ControlState;
import java.time.Instant;

/** 锁内或读路径使用的定义快照。 */
public record MxzTaskDefinitionSnapshot(
        long definitionId,
        String tenantId,
        String scenarioKey,
        int scenarioSchemaVersion,
        String title,
        String description,
        String scenarioConfigJson,
        ControlState controlState,
        long controlGeneration,
        long revision,
        Instant createdAt,
        Instant updatedAt) {{}}
""",
)

w(
    f"timeimprint-task-service-kernel/src/main/java/{BP.replace('.', '/')}/service/kernel/domain/snapshot/MxzTaskInstanceSnapshot.java",
    f"""
package {BP}.service.kernel.domain.snapshot;

import {BP}.service.kernel.domain.state.LifecycleCategory;
import java.time.Instant;

/** 锁内或读路径使用的实例快照。 */
public record MxzTaskInstanceSnapshot(
        long instanceId,
        long definitionId,
        Long triggerBindingId,
        Long scheduleGeneration,
        long definitionControlGeneration,
        String occurrenceKey,
        Instant occurrenceAt,
        Instant dueAt,
        LifecycleCategory lifecycleCategory,
        String scenarioState,
        int scenarioSchemaVersion,
        String scenarioSnapshotJson,
        String titleSnapshot,
        String descriptionSnapshot,
        long revision,
        Instant terminalAt) {{}}
""",
)

print("core kernel types updated")
