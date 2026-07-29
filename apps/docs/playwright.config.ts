import { defineConfig, devices } from '@playwright/test';

const port = 3100;

export default defineConfig({
  testDir: './test/e2e',
  outputDir: '../../output/playwright/docs-results',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  timeout: 60_000,
  workers: process.env.CI ? 2 : 4,
  reporter: process.env.CI
    ? [['html', { outputFolder: '../../output/playwright/docs-report', open: 'never' }]]
    : 'list',
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    trace: 'retain-on-failure',
  },
  webServer: {
    command: `corepack pnpm start --hostname 127.0.0.1 --port ${port}`,
    env: {
      DOCS_DEPLOYMENT_MODE: 'production',
      DOCS_INCLUDE_DRAFTS: 'false',
    },
    url: `http://127.0.0.1:${port}/healthz`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'mobile-chromium',
      use: { ...devices['Pixel 7'] },
    },
  ],
});
