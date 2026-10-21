
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Forward frontend API requests to the Spring Boot backend.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
