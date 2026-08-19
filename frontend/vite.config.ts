import { defineConfig } from 'vite'

// The sibling Selenium suite defaults its baseUrl to http://localhost:5173, so a
// silent fallback to the next free port would point it at a dead origin instead.
export default defineConfig({
  server: {
    port: 5173,
    strictPort: true
  }
})
