import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    // A porta precisa ser exatamente 5173: é a única que o Keycloak aceita como redirect URI.
    port: 5173,
    strictPort: true,
  },
});
