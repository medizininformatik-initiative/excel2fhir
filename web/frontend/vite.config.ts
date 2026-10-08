import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
export default defineConfig({ build: { rollupOptions: { output: { manualChunks(id) { if (id.includes('/node_modules/')) return 'vendor' } } } }, plugins: [react(), tailwindcss()], server: { proxy: { '/api': { target: 'http://127.0.0.1:8000', changeOrigin: false } } } })
