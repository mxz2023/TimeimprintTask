/** 把接口结果转成界面可读反馈文案。 */

export function controlStateLabel(state) {
  const map = {
    ACTIVE: "进行中",
    PAUSED: "已暂停",
    RETIRED: "已退役",
  };
  return map[state] || state || "—";
}

export function scenarioStateLabel(state) {
  const map = {
    PLANNED: "已排期",
    WAITING: "等待中",
    TRIGGERED: "已提醒（结束）",
    PENDING: "待办理",
    COMPLETED: "已完成",
    SKIPPED: "已跳过",
    CANCELLED: "已取消",
    EXPIRED: "已过期",
  };
  return map[state] || state || "—";
}

export function lifecycleLabel(cat) {
  const map = {
    ACTIVE: "进行中",
    TERMINAL: "已结束",
  };
  return map[cat] || cat || "—";
}

export function purposeLabel(purpose) {
  const map = {
    INITIAL: "到点提醒",
    CHASE: "催办提醒",
  };
  return map[purpose] || purpose || "通知";
}

export function formatShanghai(iso) {
  if (!iso) return "—";
  try {
    const d = new Date(iso);
    const datePart = new Intl.DateTimeFormat("zh-CN", {
      timeZone: "Asia/Shanghai",
      year: "numeric",
      month: "long",
      day: "numeric",
      weekday: "short",
    }).format(d);
    const timePart = new Intl.DateTimeFormat("zh-CN", {
      timeZone: "Asia/Shanghai",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
    }).format(d);
    return `${datePart} ${timePart}`;
  } catch {
    return String(iso);
  }
}

export function apiMessage(res) {
  return res?.envelope?.message || (res?.ok ? "操作已完成" : "操作未成功，请稍后重试");
}

export function noticeFromResult(res, { okTitle, errorTitle, facts } = {}) {
  if (res?.ok) {
    return {
      status: "ok",
      title: okTitle || "操作成功",
      message: apiMessage(res),
      facts: facts || [],
    };
  }
  return {
    status: "error",
    title: errorTitle || "操作未成功",
    message: apiMessage(res),
    facts: [],
  };
}
