/// <reference types="vite/client" />

interface HuntCoreDashboardConfig {
  apiBaseUrl?: string;
}

interface Window {
  __HUNTCORE_CONFIG__?: HuntCoreDashboardConfig;
}
