import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.RBAC_API_TARGET || 'http://127.0.0.1:8081'
  return {
    plugins: [vue()],
    server: {
      port: 5173,
      strictPort: true,
      proxy: { '/auth': { target }, '/api/v1': { target } },
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: { 'element-plus': ['element-plus'], vue: ['vue', 'vue-router', 'pinia'] },
        },
      },
    },
  }
})
