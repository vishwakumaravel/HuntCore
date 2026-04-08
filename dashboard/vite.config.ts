import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: process.env.HUNTCORE_DASHBOARD_BASE_PATH || "/",
  plugins: [react()],
  server: {
    port: 4173
  }
});
