<template>
  <section>
    <header class="page-head">
      <h1>运行诊断</h1>
      <p class="lead">给联调排障用。日常请用左侧「提醒 / 待办 / 收件箱」。</p>
    </header>

    <UserNotice
      v-if="notice"
      :status="notice.status"
      :title="notice.title"
      :message="notice.message"
      :facts="notice.facts"
    />

    <div class="panel" style="margin-top: 0.85rem">
      <h2>手动推进到点</h2>
      <div class="field">
        <label>到点事件编号</label>
        <input v-model="signalId" />
      </div>
      <div class="row">
        <button class="btn btn-primary" :disabled="busy || !signalId" @click="doProcess">推进</button>
        <button class="btn" :disabled="busy || !signalId" @click="doGetSignal">查看</button>
      </div>
    </div>

    <div class="panel" style="margin-top: 0.85rem">
      <h2>后台动作</h2>
      <button class="btn" :disabled="busy" @click="doListActions">刷新列表</button>
      <ul v-if="actions.length" class="simple">
        <li v-for="a in actions" :key="a.actionJobId">
          {{ a.handlerKey }} · {{ a.effectiveStatus || a.storedStatus }}
          <button class="btn" @click="actionJobId = String(a.actionJobId)">选用</button>
        </li>
      </ul>
      <div class="field" style="margin-top: 0.75rem">
        <label>动作编号</label>
        <input v-model="actionJobId" />
      </div>
      <button class="btn btn-danger" :disabled="busy || !actionJobId" @click="doRedriveAction">
        重试动作
      </button>
    </div>
  </section>
</template>

<script setup>
import { ref } from "vue";
import UserNotice from "../components/UserNotice.vue";
import {
  getSignal,
  listActionJobs,
  processSignal,
  redriveAction,
} from "../api/endpoints.js";

const busy = ref(false);
const notice = ref(null);
const signalId = ref("");
const actionJobId = ref("");
const actions = ref([]);

function setNotice(status, title, message, facts = []) {
  notice.value = { status, title, message, facts };
}

async function doProcess() {
  busy.value = true;
  try {
    const res = await processSignal(signalId.value);
    setNotice(
      res.ok ? "ok" : "error",
      res.ok ? "已推进" : "推进失败",
      res.envelope?.message || "",
    );
  } finally {
    busy.value = false;
  }
}

async function doGetSignal() {
  busy.value = true;
  try {
    const res = await getSignal(signalId.value);
    setNotice(
      res.ok ? "info" : "error",
      res.ok ? "事件状态" : "查不到",
      res.envelope?.message || "",
      res.envelope?.data?.processStatus
        ? [`状态：${res.envelope.data.processStatus}`]
        : [],
    );
  } finally {
    busy.value = false;
  }
}

async function doListActions() {
  busy.value = true;
  try {
    const res = await listActionJobs({ limit: 30 });
    actions.value = res.envelope?.data?.items || [];
    setNotice(res.ok ? "ok" : "error", res.ok ? "已刷新" : "失败", `共 ${actions.value.length} 条`);
  } finally {
    busy.value = false;
  }
}

async function doRedriveAction() {
  busy.value = true;
  try {
    const res = await redriveAction(actionJobId.value, { reason: "手动重试" });
    setNotice(res.ok ? "ok" : "error", res.ok ? "已重试" : "重试失败", res.envelope?.message || "");
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.page-head h1 {
  margin: 0 0 0.3rem;
  font-size: 1.5rem;
}

.lead {
  margin: 0;
  color: var(--muted);
}

.simple {
  margin: 0.75rem 0 0;
  padding-left: 1.1rem;
  line-height: 1.8;
}
</style>
