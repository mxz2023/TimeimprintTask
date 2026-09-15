<template>
  <section>
    <header class="page-head">
      <h1>待办</h1>
      <p class="lead">到点后会出现在「等你处理」。做完就勾掉，不做就跳过或延后。</p>
    </header>

    <div class="composer panel">
      <div class="field">
        <label>要办什么</label>
        <input v-model="title" placeholder="例如：写周报" @keyup.enter="create" />
      </div>
      <div class="field">
        <label>怎么重复</label>
        <select v-model="calType">
          <option value="ONCE">就这一次</option>
          <option value="DAILY">每天</option>
          <option value="WEEKLY">每周五</option>
          <option value="MONTHLY">每月 1 号</option>
          <option value="EVERY_N_DAYS">每 3 天</option>
        </select>
      </div>
      <div class="field">
        <label>{{ calType === "ONCE" ? "什么时候" : "从哪天开始（含时刻）" }}</label>
        <input v-model="when" type="datetime-local" />
      </div>
      <button class="btn btn-primary btn-wide" :disabled="busy || !canCreate" @click="create">
        {{ busy ? "正在创建…" : "安排好" }}
      </button>
      <UserNotice
        v-if="notice && notice.scope === 'create'"
        :status="notice.status"
        :title="notice.title"
        :message="notice.message"
        :facts="notice.facts"
      />
    </div>

    <div class="section">
      <div class="list-head">
        <h2>等你处理</h2>
        <button class="btn" :disabled="busy" @click="loadPending">刷新</button>
      </div>
      <UserNotice
        v-if="notice && notice.scope === 'todo'"
        :status="notice.status"
        :title="notice.title"
        :message="notice.message"
        :facts="notice.facts"
      />
      <ul v-if="pending.length" class="cards">
        <li v-for="i in pending" :key="i.instanceId" class="card stack">
          <div class="card-main">
            <div class="card-title">{{ i.titleSnapshot || "待办" }}</div>
            <div class="card-meta">{{ formatShanghai(i.dueAt || i.occurrenceAt) }}</div>
          </div>
          <div class="card-actions">
            <button class="btn btn-primary" :disabled="busy" @click="act(i, 'complete')">完成</button>
            <button class="btn" :disabled="busy" @click="act(i, 'skip')">跳过</button>
            <button class="btn" :disabled="busy" @click="act(i, 'snooze', 1)">延后 1 小时</button>
            <button class="btn" :disabled="busy" @click="act(i, 'snooze', 'tomorrow')">延后到明天上午</button>
          </div>
        </li>
      </ul>
      <p v-else class="muted empty">现在没有要处理的。到点后会自动出现在这里。</p>
    </div>

    <div class="section">
      <div class="list-head">
        <h2>我的安排</h2>
        <button class="btn" :disabled="busy" @click="loadRules">刷新</button>
      </div>
      <ul v-if="rules.length" class="cards">
        <li v-for="d in rules" :key="d.definitionId" class="card">
          <div class="card-main">
            <div class="card-title">{{ d.title }}</div>
            <div class="card-meta">{{ controlStateLabel(d.controlState) }}</div>
          </div>
          <div class="card-actions">
            <button
              v-if="d.controlState === 'ACTIVE'"
              class="btn"
              :disabled="busy"
              @click="ruleCmd(d, 'pause')"
            >
              暂停
            </button>
            <button
              v-else-if="d.controlState === 'PAUSED'"
              class="btn"
              :disabled="busy"
              @click="ruleCmd(d, 'resume')"
            >
              继续
            </button>
            <button class="btn btn-danger" :disabled="busy" @click="ruleCmd(d, 'retire')">删除</button>
          </div>
        </li>
      </ul>
      <p v-else class="muted empty">还没有周期安排。在上面填好后点「安排好」。</p>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import UserNotice from "../components/UserNotice.vue";
import { newRequestId } from "../api/client.js";
import {
  createDefinition,
  executeDefinitionCommand,
  executeInstanceCommand,
  listDefinitions,
  listInstances,
} from "../api/endpoints.js";
import { controlStateLabel, formatShanghai } from "../utils/feedback.js";
import {
  datetimeLocalSoon,
  isShanghaiOnceStillFuture,
  ownerParticipant,
  snoozeUntilHoursFromNow,
  snoozeUntilTomorrowMorning,
  splitDatetimeLocal,
} from "../utils/calendar.js";

const SCENARIO = "recurring_todo";
const busy = ref(false);
const title = ref("写周报");
const calType = ref("ONCE");
const when = ref(datetimeLocalSoon(5));
const pending = ref([]);
const rules = ref([]);
const notice = ref(null);

const canCreate = computed(() => {
  if (!title.value.trim() || !when.value) return false;
  if (calType.value !== "ONCE") return true;
  const { localDate, localTime } = splitDatetimeLocal(when.value);
  return isShanghaiOnceStillFuture(localDate, localTime);
});

function setNotice(scope, status, titleText, message, facts = []) {
  notice.value = { scope, status, title: titleText, message, facts };
}

function calendarConfig() {
  const { localDate, localTime } = splitDatetimeLocal(when.value);
  if (calType.value === "ONCE") {
    return { type: "ONCE", localDate, localTime, zoneId: "Asia/Shanghai" };
  }
  const base = {
    type: calType.value,
    startDate: localDate,
    localTime,
    zoneId: "Asia/Shanghai",
  };
  if (calType.value === "WEEKLY") return { ...base, weekday: 5 };
  if (calType.value === "MONTHLY") return { ...base, dayOfMonth: 1 };
  if (calType.value === "EVERY_N_DAYS") return { ...base, intervalDays: 3 };
  return base;
}

async function create() {
  if (!canCreate.value) {
    setNotice("create", "error", "时间要再晚一点", "「就这一次」请选比现在更晚的时间。");
    when.value = datetimeLocalSoon(5);
    return;
  }
  busy.value = true;
  try {
    const res = await createDefinition({
      requestId: newRequestId(),
      scenarioKey: SCENARIO,
      scenarioSchemaVersion: 1,
      title: title.value.trim(),
      description: "",
      scenarioConfig: {
        chaseOffsetsMinutes: [60, 240, 720],
        notificationExpireAfterMinutes: 1440,
        maxSnoozeCount: 3,
      },
      participants: ownerParticipant(),
      triggerBindings: [
        {
          bindingKey: "primary",
          providerKey: "calendar",
          schemaVersion: 1,
          config: calendarConfig(),
        },
      ],
    });
    if (res.ok) {
      setNotice("create", "ok", "已安排好", `「${title.value.trim()}」会按时出现在待办里。`, [
        "到点后请到「等你处理」完成或跳过。",
      ]);
      when.value = datetimeLocalSoon(5);
      await Promise.all([loadPending(), loadRules()]);
    } else {
      setNotice("create", "error", "没安排成功", res.envelope?.message || "");
    }
  } catch (e) {
    setNotice("create", "error", "没安排成功", String(e));
  } finally {
    busy.value = false;
  }
}

async function loadPending() {
  busy.value = true;
  try {
    const res = await listInstances({ scenarioKey: SCENARIO, limit: 40 });
    pending.value = (res.envelope?.data?.items || []).filter((i) => i.scenarioState === "PENDING");
  } finally {
    busy.value = false;
  }
}

async function loadRules() {
  const res = await listDefinitions({ scenarioKey: SCENARIO, limit: 50 });
  rules.value = (res.envelope?.data?.items || []).filter((d) => d.controlState !== "RETIRED");
}

async function act(i, commandKey, snoozeKind) {
  busy.value = true;
  try {
    let payload = {};
    if (commandKey === "skip") payload = { reason: "本期不需要" };
    if (commandKey === "snooze") {
      payload = {
        snoozeUntil:
          snoozeKind === "tomorrow" ? snoozeUntilTomorrowMorning() : snoozeUntilHoursFromNow(1),
      };
    }
    const res = await executeInstanceCommand(String(i.instanceId), commandKey, {
      expectedRevision: i.revision ?? 1,
      payload,
    });
    const okTitle =
      commandKey === "complete" ? "做完了" : commandKey === "skip" ? "已跳过" : "已延后";
    if (res.ok) {
      setNotice("todo", "ok", okTitle, `「${i.titleSnapshot || "待办"}」已更新。`);
      await loadPending();
    } else {
      setNotice("todo", "error", "没处理成功", res.envelope?.message || "");
    }
  } finally {
    busy.value = false;
  }
}

async function ruleCmd(d, commandKey) {
  busy.value = true;
  try {
    const res = await executeDefinitionCommand(String(d.definitionId), commandKey, {
      expectedRevision: d.revision ?? 1,
      payload: {},
    });
    if (res.ok) {
      setNotice(
        "todo",
        "ok",
        commandKey === "retire" ? "已删除" : commandKey === "pause" ? "已暂停" : "已继续",
        `「${d.title}」`,
      );
      await loadRules();
    } else {
      setNotice("todo", "error", "操作没成功", res.envelope?.message || "");
    }
  } finally {
    busy.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadPending(), loadRules()]);
});
</script>

<style scoped>
.page-head h1 {
  margin: 0 0 0.3rem;
  font-size: 1.7rem;
}

.lead {
  margin: 0 0 1.1rem;
  color: var(--muted);
  line-height: 1.5;
}

.composer {
  margin-bottom: 1.5rem;
}

.btn-wide {
  width: 100%;
  padding: 0.65rem 1rem;
}

.section {
  margin-bottom: 1.5rem;
}

.list-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.65rem;
}

.list-head h2 {
  margin: 0;
  font-size: 1.05rem;
}

.cards {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
  padding: 0.9rem 1rem;
  border-radius: 12px;
  border: 1px solid var(--line);
  background: var(--surface);
}

.card.stack {
  flex-direction: column;
  align-items: stretch;
}

.card-title {
  font-weight: 600;
}

.card-meta {
  margin-top: 0.2rem;
  font-size: 0.85rem;
  color: var(--muted);
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.empty {
  margin: 0.25rem 0 0;
}
</style>
