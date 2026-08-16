import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = (env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080').replace(/\/$/, '')

  return {
    plugins: [vue()],
    server: {
      host: '127.0.0.1',
      port: 5173,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
          configure(proxy) {
            proxy.on('error', (_error, _request, response) => {
              if (!response.headersSent) {
                response.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
              }
              response.end(JSON.stringify({
                success: false,
                message: `无法连接后端服务 ${proxyTarget}，请确认 Spring Boot 已启动`,
                data: null,
              }))
            })
          },
        },
      },
    },
  }
})
