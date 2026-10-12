import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // src/domain/contract.js imports ../../contract/assignment-status.json,
  // which lives outside the dashboard folder.
  server: { fs: { allow: ['..'] } },
});
