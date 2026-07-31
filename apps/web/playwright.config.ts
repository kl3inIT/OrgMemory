import { defineConfig, devices } from "@playwright/test"

const port = Number(process.env.PLAYWRIGHT_PORT ?? 4173)
const requestedChannel = process.env.PLAYWRIGHT_CHANNEL
if (requestedChannel && requestedChannel !== "msedge") {
  throw new Error(
    `Unsupported PLAYWRIGHT_CHANNEL "${requestedChannel}"; expected "msedge" or unset.`,
  )
}
const browserChannel = requestedChannel === "msedge" ? requestedChannel : undefined

export default defineConfig({
  testDir: "./test/e2e",
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  failOnFlakyTests: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI
    ? [["line"], ["html", { open: "never", outputFolder: "../../output/playwright/report" }]]
    : "list",
  outputDir: "../../output/playwright/test-results",
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        channel: browserChannel,
        ...(process.env.DESIGN_QA_CAPTURE ? { deviceScaleFactor: 1.5 } : {}),
      },
    },
  ],
  webServer: {
    command: `pnpm dev --host 127.0.0.1 --port ${port} --strictPort`,
    url: `http://127.0.0.1:${port}`,
    reuseExistingServer: !process.env.CI,
    stdout: "ignore",
    stderr: "pipe",
  },
})
