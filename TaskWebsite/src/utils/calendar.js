/** Asia/Shanghai local date/time about minutesAhead from now. */
export function shanghaiLocalParts(minutesAhead = 2) {
  const d = new Date(Date.now() + minutesAhead * 60 * 1000);
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
    hourCycle: "h23",
  }).formatToParts(d);
  const get = (t) => parts.find((p) => p.type === t)?.value;
  let hour = get("hour") || "00";
  if (hour === "24") hour = "00";
  hour = hour.padStart(2, "0");
  return {
    localDate: `${get("year")}-${get("month")}-${get("day")}`,
    localTime: `${hour}:${(get("minute") || "00").padStart(2, "0")}:${(get("second") || "00").padStart(2, "0")}`,
  };
}

/** Value for <input type="datetime-local">（按上海墙钟）。 */
export function datetimeLocalSoon(minutesAhead = 5) {
  const p = shanghaiLocalParts(minutesAhead);
  return `${p.localDate}T${p.localTime.slice(0, 5)}`;
}

export function splitDatetimeLocal(value) {
  if (!value || !value.includes("T")) return { localDate: "", localTime: "" };
  const [localDate, timePart] = value.split("T");
  const localTime = timePart.length === 5 ? `${timePart}:00` : timePart.slice(0, 8);
  return { localDate, localTime };
}

export function formatDatetimeLocalFriendly(value) {
  const { localDate, localTime } = splitDatetimeLocal(value);
  if (!localDate || !localTime) return "";
  try {
    const d = new Date(`${localDate}T${localTime}+08:00`);
    return new Intl.DateTimeFormat("zh-CN", {
      timeZone: "Asia/Shanghai",
      month: "long",
      day: "numeric",
      weekday: "short",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    }).format(d);
  } catch {
    return `${localDate} ${localTime}`;
  }
}

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const TIME_RE = /^\d{2}:\d{2}:\d{2}$/;

export function isValidLocalDate(value) {
  return DATE_RE.test(value || "");
}

export function isValidLocalTime(value) {
  return TIME_RE.test(value || "");
}

export function isShanghaiOnceStillFuture(localDate, localTime) {
  if (!isValidLocalDate(localDate) || !isValidLocalTime(localTime)) return false;
  const asUtc = new Date(`${localDate}T${localTime}+08:00`);
  return Number.isFinite(asUtc.getTime()) && asUtc.getTime() > Date.now();
}

export function onceCalendarBinding(localDate, localTime) {
  return [
    {
      bindingKey: "primary",
      providerKey: "calendar",
      schemaVersion: 1,
      config: {
        type: "ONCE",
        localDate,
        localTime,
        zoneId: "Asia/Shanghai",
      },
    },
  ];
}

export function ownerParticipant() {
  return [{ principalType: "USER", principalId: "local-actor", roleCode: "OWNER" }];
}

/** 延后：从现在起加 hours，返回 UTC ISO（API snoozeUntil）。 */
export function snoozeUntilHoursFromNow(hours) {
  const d = new Date(Date.now() + hours * 3600 * 1000);
  return d.toISOString().replace(/\.\d{3}Z$/, "Z");
}

/** 延后到明天上午 9 点（上海）。 */
export function snoozeUntilTomorrowMorning() {
  const nowParts = shanghaiLocalParts(0);
  const base = new Date(`${nowParts.localDate}T12:00:00+08:00`);
  base.setDate(base.getDate() + 1);
  const y = base.getFullYear();
  const m = String(base.getMonth() + 1).padStart(2, "0");
  const d = String(base.getDate()).padStart(2, "0");
  return new Date(`${y}-${m}-${d}T09:00:00+08:00`).toISOString().replace(/\.\d{3}Z$/, "Z");
}
