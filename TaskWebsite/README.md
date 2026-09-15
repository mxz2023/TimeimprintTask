# TimeImprint TaskWebsite

本地 **Vue 3 + Vite** 用户向任务界面（非正式 P01 产品 GUI）。

| 入口 | 做什么 |
| --- | --- |
| 今天 | 一眼看到待处理与未读，快捷去定提醒 / 安排待办 |
| 提醒 | 写一句 + 选时间 → 设好；列表里暂停 / 删除 |
| 待办 | 安排周期或一次性；到点后在「等你处理」完成 / 跳过 / 延后 |
| 收件箱 | 看通知、标已读 |
| 高级 · 运行诊断 | 仅联调（默认不打扰） |

## 启动

```bash
cd TaskWebsite
npm install
npm run dev
```

后端需在 `127.0.0.1:18080`（Vite 已代理 `/api` `/internal` `/actuator`）。
