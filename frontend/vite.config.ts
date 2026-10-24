import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
import tailwindcss from "@tailwindcss/vite";
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "");
  return {
    plugins: [vue(), tailwindcss()],
    server: {
      proxy: {
        "/api": {
          target: env.BACKEND_URL || "http://127.0.0.1:8080",
          changeOrigin: true,
        },
      },
    },
  };
});
