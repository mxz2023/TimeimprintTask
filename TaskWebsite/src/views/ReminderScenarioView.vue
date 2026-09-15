<template>
  <section>
    <header class="page-head">
      <h1>提醒</h1>
      <p class="lead">写一句事，选个时间。到点会提醒你，不用再点「完成」。</p>
    </header>

    <div class="composer panel">
      <div class="field">
        <label>提醒我什么</label>
        <input v-model="title" placeholder="例如：记得喝水" @keyup.enter="create" />
      </div>
      <div class="field">
        <label>什么时候</label>
        <input v-model="when" type="datetime-local" />
      </div>
      <p v-if="whenHint" class="when-hint">将在 {{ whenHint }} 提醒你</p>
      <button class="btn btn-primary btn-wide" :disabled="busy || !canCreate" @click="create">
        {{ busy ? "正在创建…" : "设好提醒" }}
      </button>
      <UserNotice
        v-if="notice"
        :status="notice.status"
        :title="notice.title"
        :message="notice.message"
        :facts="notice.facts"
      />
    </div>

    <div class="list-head">
      <h2>我的提醒</h2>
      <button class="btn" :disabled="busy" @click="load">刷新</button>
    </div>

    <ul v-if="items.length" class="cards">
      <li v-for="d in items" :key="d.definitionId" class="card">
        <div class="card-main">
          <div class="card-title">{{ d.title }}</div>
          <div class="card-meta">{{ controlStateLabel(d.controlState) }}</div>
        </div>
        <div class="card-actions">
          <button
            v-if="d.controlState === 'ACTIVE'"
            class="btn"
            :disabled="busy"
            @click="cmd(d, 'pause')"
          >
            暂停
          </button>
          <button
            v-else-if="d.controlState === 'PAUSED'"
            class="btn"
            :disabled="busy"
            @click="cmd(d, 'resume')"
          >
            继续
          </button>
          <button class="btn btn-danger" :disabled="busy" @click="cmd(d, 'retire')">删除</button>
        </div>
      </li>
    </ul>
    <p v-else class="muted empty">还没有提醒。在上面填好，点「设好提醒」。</p>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import UserNotice from "../components/UserNotice.vue";
import { newRequestId } from "../api/client.js";
import { createDefinition, executeDefinitionCommand, listDefinitions } from "../api/endpoints.js";
import { controlStateLabel } from "../utils/feedback.js";
import {
  datetimeLocalSoon,
  formatDatetimeLocalFriendly,
  isShanghaiOnceStillFuture,
  onceCalendarBinding,
  ownerParticipant,
  splitDatetimeLocal,
} from "../utils/calendar.js";

const SCENARIO = "reminder";
const busy = ref(false);
const title = ref("记得喝水");
const when = ref(datetimeLocalSoon(5));
const items = ref([]);
const notice = ref(null);

const whenHint = computed(() => formatDatetimeLocalFriendly(when.value));

const canCreate = computed(() => {
  if (!title.value.trim() || !when.value) return false;
  const { localDate, localTime } = splitDatetimeLocal(when.value);
  return isShanghaiOnceStillFuture(localDate, localTime);
});

watch(when, () => {
  if (notice.value?.status === "error") notice.value = null;
});

async function load() {
  busy.value = true;
  try {
    const res = await listDefinitions({ scenarioKey: SCENARIO, limit: 50 });
    items.value = (res.envelope?.data?.items || []).filter((d) => d.controlState !== "RETIRED");
  } finally {
    busy.value = false;
  }
}

async function create() {
  if (!canCreate.value) {
    notice.value = {
      status: "error",
      title: "时间要再晚一点",
      message: "请选一个比现在更晚的时间。",
    };
    when.value = datetimeLocalSoon(5);
    return;
  }
  busy.value = true;
  notice.value = null;
  try {
    const { localDate, localTime } = splitDatetimeLocal(when.value);
    const res = await createDefinition({
      requestId: newRequestId(),
      scenarioKey: SCENARIO,
      scenarioSchemaVersion: 1,
      title: title.value.trim(),
      description: "",
      scenarioConfig: {},
      participants: ownerParticipant(),
      triggerBindings: onceCalendarBinding(localDate, localTime),
    });
    if (res.ok) {
      notice.value = {
        status: "ok",
        title: "提醒已设好",
        message: `到点会提醒你「${title.value.trim()}」。通知会出现在收件箱。`,
        facts: [`时间：${whenHint.value}`],
      };
      when.value = datetimeLocalSoon(5);
      await load();
    } else {
      notice.value = {
        status: "error",
        title: "没设成功",
        message: res.envelope?.message || "请换个稍晚的时间再试。",
      };
    }
  } catch (e) {
    notice.value = { status: "error", title: "没设成功", message: String(e) };
  } finally {
    busy.value = false;
  }
}

async function cmd(d, commandKey) {
  busy.value = true;
  try {
    const labels = {
      pause: "已暂停",
      resume: "已继续",
      retire: "已删除",
    };
    const res = await executeDefinitionCommand(String(d.definitionId), commandKey, {
      expectedRevision: d.revision ?? 1,
      payload: {},
    });
    if (res.ok) {
      notice.value = {
        status: "ok",
        title: `${labels[commandKey] || "已更新"}「${d.title}」`,
        message: "",
      };
      await load();
    } else {
      notice.value = {
        status: "error",
        title: "操作没成功",
        message: res.envelope?.message || "",
      };
    }
  } finally {
    busy.value = false;
  }
}

onMounted(load);
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
  margin-bottom: 1.4rem;
}

.when-hint {
  margin: -0.25rem 0 0.85rem;
  color: var(--accent);
  font-weight: 600;
}

.btn-wide {
  width: 100%;
  justify-content: center;
  padding: 0.65rem 1rem;
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
  padding: 0.5rem 0 1rem;
}
</style>
