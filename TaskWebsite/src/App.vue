<template>
  <div class="shell">
    <aside class="side">
      <div class="brand">
        <div class="brand-mark">记</div>
        <div>
          <div class="brand-name">时光印记</div>
          <div class="brand-sub">提醒与待办</div>
        </div>
      </div>

      <nav>
        <RouterLink class="nav-link" to="/">今天</RouterLink>
        <RouterLink class="nav-link" to="/reminders">提醒</RouterLink>
        <RouterLink class="nav-link" to="/todos">待办</RouterLink>
        <RouterLink class="nav-link" to="/inbox">
          收件箱
          <span v-if="unread > 0" class="badge">{{ unread > 99 ? "99+" : unread }}</span>
        </RouterLink>
      </nav>

      <RouterLink class="nav-foot" to="/ops">高级 · 运行诊断</RouterLink>
    </aside>
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from "vue";
import { unreadCount } from "./api/endpoints.js";

const unread = ref(0);
let timer;

async function refreshUnread() {
  try {
    const res = await unreadCount();
    if (res.ok) {
      unread.value = Number(res.envelope?.data?.unreadCount ?? res.envelope?.data?.count ?? 0);
    }
  } catch {
    /* ignore */
  }
}

onMounted(() => {
  refreshUnread();
  timer = setInterval(refreshUnread, 15000);
});
onUnmounted(() => clearInterval(timer));
</script>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: 220px 1fr;
  min-height: 100vh;
}

.side {
  display: flex;
  flex-direction: column;
  padding: 1.35rem 0.85rem 1rem;
  border-right: 1px solid var(--line);
  background: rgba(255, 253, 248, 0.9);
}

.brand {
  display: flex;
  gap: 0.7rem;
  align-items: center;
  margin-bottom: 1.5rem;
  padding: 0 0.4rem;
}

.brand-mark {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 0.75rem;
  display: grid;
  place-items: center;
  background: var(--accent);
  color: #f4fffb;
  font-weight: 700;
  font-size: 1.1rem;
}

.brand-name {
  font-weight: 700;
  font-size: 1.05rem;
}

.brand-sub {
  font-size: 0.8rem;
  color: var(--muted);
}

nav {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  flex: 1;
}

.nav-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ink);
  padding: 0.7rem 0.85rem;
  border-radius: 10px;
  font-weight: 500;
}

.nav-link.router-link-active {
  background: var(--accent);
  color: #f5fffb;
}

.badge {
  min-width: 1.35rem;
  padding: 0.1rem 0.4rem;
  border-radius: 999px;
  background: #c45c3e;
  color: #fff;
  font-size: 0.72rem;
  text-align: center;
}

.nav-link.router-link-active .badge {
  background: #fff;
  color: var(--accent);
}

.nav-foot {
  margin-top: auto;
  padding: 0.5rem 0.85rem;
  font-size: 0.78rem;
  color: var(--muted);
}

.main {
  padding: 1.5rem 1.6rem 2.5rem;
  max-width: 720px;
}

@media (max-width: 860px) {
  .shell {
    grid-template-columns: 1fr;
  }

  .side {
    border-right: none;
    border-bottom: 1px solid var(--line);
  }

  nav {
    flex-direction: row;
    flex-wrap: wrap;
  }

  .nav-foot {
    display: none;
  }
}
</style>
