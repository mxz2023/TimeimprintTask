<template>
  <section>
    <header class="page-head">
      <h1>收件箱</h1>
      <p class="lead">提醒和待办到点后的通知都在这里。</p>
    </header>

    <div class="toolbar">
      <label class="toggle">
        <input v-model="unreadOnly" type="checkbox" @change="load" />
        只看未读
      </label>
      <button class="btn" :disabled="busy" @click="load">刷新</button>
    </div>

    <UserNotice
      v-if="notice"
      :status="notice.status"
      :title="notice.title"
      :message="notice.message"
      :facts="notice.facts"
    />

    <ul v-if="items.length" class="cards">
      <li v-for="m in items" :key="m.inboxId" class="card" :class="{ unread: !m.readAt }">
        <div class="card-main">
          <div class="card-title">{{ m.title }}</div>
          <div class="card-meta">
            {{ scenarioLabel(m.scenarioKey) }} · {{ purposeLabel(m.purpose) }}
            <span v-if="m.readAt"> · 已读</span>
          </div>
        </div>
        <button v-if="!m.readAt" class="btn btn-primary" :disabled="busy" @click="mark(m.inboxId)">
          标已读
        </button>
      </li>
    </ul>
    <p v-else class="muted empty">
      {{ unreadOnly ? "没有未读通知。" : "收件箱是空的。设好提醒或待办并到点后，会出现在这里。" }}
    </p>
  </section>
</template>

<script setup>
import { onMounted, ref } from "vue";
import UserNotice from "../components/UserNotice.vue";
import { listInbox, markInboxRead } from "../api/endpoints.js";
import { purposeLabel } from "../utils/feedback.js";

const busy = ref(false);
const unreadOnly = ref(true);
const items = ref([]);
const notice = ref(null);

function scenarioLabel(key) {
  if (key === "reminder") return "提醒";
  if (key === "recurring_todo") return "待办";
  return "通知";
}

async function load() {
  busy.value = true;
  try {
    const res = await listInbox({ unreadOnly: unreadOnly.value, limit: 50 });
    items.value = res.envelope?.data?.items || [];
    if (!res.ok) {
      notice.value = {
        status: "error",
        title: "加载失败",
        message: res.envelope?.message || "",
      };
    } else {
      notice.value = null;
    }
  } finally {
    busy.value = false;
  }
}

async function mark(id) {
  busy.value = true;
  try {
    const res = await markInboxRead(id);
    if (res.ok) {
      notice.value = { status: "ok", title: "已读", message: "" };
      await load();
    } else {
      notice.value = {
        status: "error",
        title: "没标上",
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
  margin: 0 0 1rem;
  color: var(--muted);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.85rem;
}

.toggle {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--muted);
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

.card.unread {
  border-color: #b7d4c6;
  background: #f2faf6;
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
  margin-top: 0.5rem;
}
</style>
