import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// En desarrollo, /ajedrez se reenvía al WildFly local para evitar CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/ajedrez': 'http://localhost:8080' },
  },
})
