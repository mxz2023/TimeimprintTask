<template>
  <section>
    <header class="hero">
      <h1>{{ greeting }}</h1>
      <p class="lead">要记的事放这里，到点会提醒你；要办的事到点后在「待办」里勾掉。</p>
    </header>

    <div class="actions">
      <RouterLink class="action primary" to="/reminders">
        <span class="action-title">定一个提醒</span>
        <span class="action-desc">到点叮一声，不用再点完成</span>
      </RouterLink>
      <RouterLink class="action" to="/todos">
        <span class="action-title">安排待办</span>
        <span class="action-desc">到点后由你完成或跳过</span>
      </RouterLink>
    </div>

    <div v-if="loading" class="muted block">正在看看今天有什么…</div>

    <template v-else>
      <section v-if="pending.length" class="block">
        <div class="block-head">
          <h2>等你处理</h2>
          <RouterLink to="/todos">全部</RouterLink>
        </div>
        <ul class="cards">
          <li v-for="i in pending.slice(0, 5)" :key="i.instanceId" class="card">
            <div>
              <div class="card-title">{{ i.titleSnapshot || "待办" }}</div>
              <div class="card-meta">{{ formatShanghai(i.dueAt || i.occurrenceAt) }}</div>
            </div>
            <RouterLink class="btn btn-primary" :to="'/todos'">去处理</RouterLink>
          </li>
        </ul>
      </section>

      <section v-if="unreadItems.length" class="block">
        <div class="block-head">
          <h2>未读通知</h2>
          <RouterLink to="/inbox">收件箱</RouterLink>
        </div>
        <ul class="cards">
          <li v-for="m in unreadItems.slice(0, 5)" :key="m.inboxId" class="card">
            <div>
              <div class="card-title">{{ m.title }}</div>
              <div class="card-meta">{{ purposeLabel(m.purpose) }}</div>
            </div>
            <button class="btn" :disabled="busy" @click="mark(m.inboxId)">标已读</button>
          </li>
        </ul>
      </section>

      <section v-if="!pending.length && !unreadItems.length" class="empty">
        <p>今天很清静。</p>
        <p class="muted">点上面「定一个提醒」或「安排待办」开始。</p>
      </section>
    </template>

    <UserNotice
      v-if="notice"
      :status="notice.status"
      :title="notice.title"
      :message="notice.message"
      :facts="notice.facts"
    />
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import UserNotice from "../components/UserNotice.vue";
import { listInbox, listInstances, markInboxRead } from "../api/endpoints.js";
import { formatShanghai, purposeLabel } from "../utils/feedback.js";

const loading = ref(true);
const busy = ref(false);
const pending = ref([]);
const unreadItems = ref([]);
const notice = ref(null);

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 12) return "早上好";
  if (h < 18) return "下午好";
  return "晚上好";
});

async function refresh() {
  loading.value = true;
  try {
    const [inst, inbox] = await Promise.all([
      listInstances({ scenarioKey: "recurring_todo", limit: 40 }),
      listInbox({ unreadOnly: true, limit: 20 }),
    ]);
    pending.value = (inst.envelope?.data?.items || []).filter((i) => i.scenarioState === "PENDING");
    unreadItems.value = inbox.envelope?.data?.items || [];
  } finally {
    loading.value = false;
  }
}

async function mark(id) {
  busy.value = true;
  try {
    const res = await markInboxRead(id);
    if (res.ok) {
      notice.value = { status: "ok", title: "已读", message: "这条通知收下了。" };
      await refresh();
    } else {
      notice.value = { status: "error", title: "没标上", message: res.envelope?.message || "" };
    }
  } finally {
    busy.value = false;
  }
}

onMounted(refresh);
</script>

<style scoped>
.hero h1 {
  margin: 0 0 0.35rem;
  font-size: 1.85rem;
}

.lead {
  margin: 0 0 1.25rem;
  color: var(--muted);
  line-height: 1.55;
  font-size: 1.02rem;
}

.actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
  margin-bottom: 1.5rem;
}

.action {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 1rem 1.1rem;
  border-radius: 14px;
  border: 1px solid var(--line);
  background: var(--surface);
  color: inherit;
}

.action.primary {
  background: var(--accent);
  border-color: var(--accent);
  color: #f5fffb;
}

.action-title {
  font-weight: 700;
  font-size: 1.05rem;
}

.action-desc {
  font-size: 0.86rem;
  opacity: 0.85;
}

.block {
  margin-bottom: 1.35rem;
}

.block-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 0.55rem;
}

.block-head h2 {
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
  padding: 0.85rem 1rem;
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

.empty {
  padding: 1.5rem 0;
  text-align: center;
}

.empty p {
  margin: 0.25rem 0;
}

@media (max-width: 640px) {
  .actions {
    grid-template-columns: 1fr;
  }
}
</style>
