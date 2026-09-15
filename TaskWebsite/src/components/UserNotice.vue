<template>
  <div v-if="status" class="notice" :class="status" role="status">
    <div class="notice-head">
      <strong>{{ title }}</strong>
      <span class="pill" :class="pillClass">{{ badge }}</span>
    </div>
    <p v-if="message" class="notice-msg">{{ message }}</p>
    <ul v-if="facts?.length" class="facts">
      <li v-for="(f, i) in facts" :key="i">{{ f }}</li>
    </ul>
  </div>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  status: { type: String, default: "" }, // ok | error | info | warn
  title: { type: String, default: "" },
  message: { type: String, default: "" },
  facts: { type: Array, default: () => [] },
});

const badge = computed(() => {
  switch (props.status) {
    case "ok":
      return "成功";
    case "error":
      return "未成功";
    case "warn":
      return "注意";
    default:
      return "提示";
  }
});

const pillClass = computed(() => {
  if (props.status === "ok") return "ok";
  if (props.status === "error") return "bad";
  if (props.status === "warn") return "warn";
  return "";
});
</script>

<style scoped>
.notice {
  margin-top: 0.85rem;
  padding: 0.85rem 0.95rem;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: #f7f6f2;
}

.notice.ok {
  border-color: #b7d4c6;
  background: #f2faf6;
}

.notice.error {
  border-color: #e2bdbd;
  background: #fbf3f3;
}

.notice.warn {
  border-color: #e2c9a0;
  background: #fbf6ee;
}

.notice.info {
  border-color: #c5d0e0;
  background: #f4f7fb;
}

.notice-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.notice-head strong {
  font-size: 0.98rem;
}

.notice-msg {
  margin: 0.45rem 0 0;
  color: var(--muted);
  line-height: 1.5;
}

.facts {
  margin: 0.55rem 0 0;
  padding-left: 1.15rem;
  color: var(--ink);
  line-height: 1.65;
}
</style>
