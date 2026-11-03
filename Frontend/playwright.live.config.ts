import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  testMatch: '**/live-auth.spec.ts',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    { name: 'live-desktop', use: { ...devices['Desktop Chrome'] } },
  ],
  // The central PowerShell runner owns the backend JAR and Vite lifecycles.
  // Keeping service ownership outside Playwright avoids orphaned Windows
  // wrapper processes while still testing the real browser-to-API flow.
});
