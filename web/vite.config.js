import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/hundsun': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true,
        // product-install 的 mvn 可能跑很久，避免代理先断开造成前端一直转圈
        timeout: 35 * 60 * 1000,
        proxyTimeout: 35 * 60 * 1000
      }
    }
  }
})
