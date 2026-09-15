/**
 * 统一调用后端信封 API（docs/04-API.md）。
 * 开发态经 Vite 代理到 127.0.0.1:18080。
 */

export function newRequestId() {
  if (globalThis.crypto?.randomUUID) {
    return crypto.randomUUID().toLowerCase();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export async function apiRequest(method, path, { query, body } = {}) {
  const url = new URL(path, window.location.origin);
  if (query) {
    Object.entries(query).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== "") {
        url.searchParams.set(k, String(v));
      }
    });
  }

  const headers = { Accept: "application/json" };
  const init = { method, headers };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    init.body = typeof body === "string" ? body : JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(url.pathname + url.search, init);
  } catch (err) {
    return {
      ok: false,
      httpStatus: 0,
      envelope: {
        code: "NETWORK_ERROR",
        message: err?.message || "网络请求失败（后端是否已启动？）",
        traceId: null,
        data: null,
      },
      rawText: String(err),
    };
  }

  const rawText = await response.text();
  let envelope;
  try {
    envelope = rawText ? JSON.parse(rawText) : null;
  } catch {
    envelope = {
      code: "NON_JSON",
      message: "响应不是 JSON",
      traceId: null,
      data: rawText,
    };
  }

  return {
    ok: response.ok && envelope?.code === "OK",
    httpStatus: response.status,
    envelope,
    rawText,
  };
}

export const api = {
  get: (path, query) => apiRequest("GET", path, { query }),
  post: (path, body, query) => apiRequest("POST", path, { body, query }),
};
