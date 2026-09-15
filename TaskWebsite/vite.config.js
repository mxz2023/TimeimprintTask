import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

/** 本地联调：浏览器访问 Vite，API 代理到 Task 后端 18080 */
export default defineConfig({
  plugins: [vue()],
  server: {
    host: "127.0.0.1",
    port: 5173,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:18080",
        changeOrigin: true,
      },
      "/internal": {
        target: "http://127.0.0.1:18080",
        changeOrigin: true,
      },
      "/actuator": {
        target: "http://127.0.0.1:18080",
        changeOrigin: true,
      },
    },
  },
});
