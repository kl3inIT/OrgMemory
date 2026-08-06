import { expect, test, type Page } from "@playwright/test"

const ORGANIZATION_ID = "11111111-1111-4111-8111-111111111111"
const USER_ID = "22222222-2222-4222-8222-222222222222"
const GATEWAY_ID = "33333333-3333-4333-8333-333333333333"

test("admin connects providers and can restore the deployment model route", async ({
  page,
}, testInfo) => {
  const browserErrors: string[] = []
  const requests: string[] = []
  let assistantOverride = true
  page.on("pageerror", (error) => browserErrors.push(error.message))
  page.on("console", (message) => {
    if (message.type() === "error") browserErrors.push(message.text())
  })

  await modelSettingsHarness(page, requests, () => assistantOverride, () => {
    assistantOverride = false
  })
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.emulateMedia({ colorScheme: "dark" })
  await page.goto("/admin/language-models")

  await expect(page.getByRole("heading", { name: "Language Models" })).toBeVisible()
  await expect(page.getByText("Gateways & routers")).toBeVisible()
  await expect(page.getByText("Self-hosted & custom")).toBeVisible()
  await expect(page.getByText("9Router primary").first()).toBeVisible()
  await expect(page.getByText("Organization override", { exact: true })).toBeVisible()
  await expect(page.getByText("RAG pipeline & prompt routes")).toBeVisible()
  await expect(page.getByText("Keyword planning", { exact: true })).toBeVisible()
  await expect(page.getByText("Graph extraction", { exact: true })).toBeVisible()
  await expect(page.getByText(/newly enqueued graph jobs/)).toBeVisible()
  await expect(page.getByRole("button", { name: /reindex/i })).toHaveCount(0)
  for (const slug of [
    "openai",
    "anthropic",
    "nine-router",
    "openrouter",
    "litellm",
    "ollama",
    "openai-compatible",
  ]) {
    await expect(page.locator(`[data-provider-logo="${slug}"]`).first()).toBeVisible()
  }

  await page.getByRole("button", { name: "OpenAI OpenAI Connect" }).click()
  await expect(page.getByRole("heading", { name: "Set up OpenAI" })).toBeVisible()
  await expect(page.getByRole("heading", { name: "Credentials" })).toBeVisible()
  await expect(page.getByRole("heading", { name: "Models" })).toBeVisible()
  await expect(page.getByText("Organization-governed access")).toBeVisible()
  await page.getByLabel("API key").fill("not-a-real-secret")
  await expect(page.getByRole("button", { name: "Test connection" })).toBeEnabled()
  await page.getByRole("button", { name: "Test connection" }).click()
  await expect(page.getByText("GPT-5.6 Sol")).toBeVisible()
  await expect(page.getByText("GPT-5.6 Terra")).toBeVisible()
  await testInfo.attach("provider-setup-modal", {
    body: await page.screenshot(),
    contentType: "image/png",
  })
  await page.getByRole("button", { name: "Close" }).click()

  await page.getByRole("button", { name: "Use deployment default" }).first().click()
  await expect(page.getByText("Deployment default restored.")).toBeVisible()
  await expect(page.getByText("Deployment default", { exact: true })).toHaveCount(3)

  expect(requests).toContain("DELETE /api/admin/ai/routes/ASSISTANT_CHAT")
  expect(browserErrors).toEqual([])
})

async function modelSettingsHarness(
  page: Page,
  requests: string[],
  hasAssistantOverride: () => boolean,
  clearAssistantOverride: () => void,
) {
  await page.route("**/api/**", async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    requests.push(`${request.method()} ${url.pathname}`)

    if (url.pathname === "/api/session") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          authenticated: true,
          name: "Organization Admin",
          email: "admin@example.test",
          userId: USER_ID,
          organizationId: ORGANIZATION_ID,
          departmentId: null,
          clearance: "STANDARD",
          canManageMembers: true,
        }),
      })
    }
    if (url.pathname === "/api/session/csrf") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        headers: { "set-cookie": "XSRF-TOKEN=model-settings-csrf; Path=/" },
        body: JSON.stringify({
          headerName: "X-XSRF-TOKEN",
          parameterName: "_csrf",
          token: "model-settings-csrf",
        }),
      })
    }
    if (url.pathname === "/api/me") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          userId: USER_ID,
          organizationId: ORGANIZATION_ID,
          name: "Organization Admin",
          email: "admin@example.test",
          departmentId: null,
          departmentName: null,
          clearance: "STANDARD",
        }),
      })
    }
    if (url.pathname === "/api/admin/ai/providers") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          {
            preset: "OPENAI",
            displayName: "OpenAI",
            vendorName: "OpenAI",
            category: "DIRECT_PROVIDER",
            protocol: "OPENAI_COMPATIBLE",
            defaultBaseUrl: "https://api.openai.com/v1",
            baseUrlEditable: false,
          },
          {
            preset: "ANTHROPIC",
            displayName: "Claude",
            vendorName: "Anthropic",
            category: "DIRECT_PROVIDER",
            protocol: "ANTHROPIC_MESSAGES",
            defaultBaseUrl: "https://api.anthropic.com",
            baseUrlEditable: false,
          },
          {
            preset: "NINE_ROUTER",
            displayName: "9Router",
            vendorName: "9Router",
            category: "GATEWAY_ROUTER",
            protocol: "OPENAI_COMPATIBLE",
            defaultBaseUrl: "http://localhost:20128/v1",
            baseUrlEditable: true,
          },
          {
            preset: "OPENROUTER",
            displayName: "OpenRouter",
            vendorName: "OpenRouter",
            category: "GATEWAY_ROUTER",
            protocol: "OPENAI_COMPATIBLE",
            defaultBaseUrl: "https://openrouter.ai/api/v1",
            baseUrlEditable: false,
          },
          {
            preset: "LITELLM",
            displayName: "LiteLLM Proxy",
            vendorName: "LiteLLM",
            category: "GATEWAY_ROUTER",
            protocol: "OPENAI_COMPATIBLE",
            defaultBaseUrl: "http://localhost:4000/v1",
            baseUrlEditable: true,
          },
          {
            preset: "OLLAMA",
            displayName: "Ollama",
            vendorName: "Ollama",
            category: "SELF_HOSTED_CUSTOM",
            protocol: "OPENAI_COMPATIBLE",
            defaultBaseUrl: "http://localhost:11434/v1",
            baseUrlEditable: true,
          },
          {
            preset: "OPENAI_COMPATIBLE",
            displayName: "OpenAI-Compatible",
            vendorName: "Custom",
            category: "SELF_HOSTED_CUSTOM",
            protocol: "OPENAI_COMPATIBLE",
            defaultBaseUrl: "https://models.example.test/v1",
            baseUrlEditable: true,
          },
        ]),
      })
    }
    if (url.pathname === "/api/admin/ai/gateways") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          {
            id: GATEWAY_ID,
            gatewayKey: "nine-router-primary",
            displayName: "9Router primary",
            preset: "NINE_ROUTER",
            category: "GATEWAY_ROUTER",
            protocol: "OPENAI_COMPATIBLE",
            supportsOpenAiReasoningEffort: true,
            baseUrl: "http://localhost:20128/v1",
            requestTimeoutSeconds: 60,
            enabled: true,
            version: 3,
            credentialSet: true,
          },
        ]),
      })
    }
    if (url.pathname === "/api/admin/ai/gateways/test" && request.method() === "POST") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          authenticated: true,
          models: [
            { id: "gpt-5.6-sol", displayName: "GPT-5.6 Sol" },
            { id: "gpt-5.6-terra", displayName: "GPT-5.6 Terra" },
          ],
        }),
      })
    }
    if (url.pathname === "/api/admin/ai/routes" && request.method() === "GET") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          hasAssistantOverride()
            ? {
                workload: "ASSISTANT_CHAT",
                gatewayKey: "nine-router-primary",
                gatewayProfileId: GATEWAY_ID,
                modelId: "openai/gpt-5",
                source: "ORGANIZATION_OVERRIDE",
                editable: true,
                version: 1,
              }
            : {
                workload: "ASSISTANT_CHAT",
                gatewayKey: "openai",
                gatewayProfileId: null,
                modelId: "gpt-5.6-sol",
                source: "DEPLOYMENT_DEFAULT",
                editable: true,
                version: 0,
              },
          {
            workload: "PROMPT_EXECUTION",
            gatewayKey: "openai",
            gatewayProfileId: null,
            modelId: "gpt-5.6-sol",
            source: "DEPLOYMENT_DEFAULT",
            editable: true,
            version: 0,
            lifecycleNote: "Changes apply to subsequent requests.",
          },
          {
            workload: "KEYWORD_PLANNING",
            gatewayKey: "openai",
            gatewayProfileId: null,
            modelId: "gpt-5.6-sol",
            openAiReasoningEffort: null,
            source: "DEPLOYMENT_DEFAULT",
            editable: true,
            version: 0,
            lifecycleNote: "Changes apply to subsequent requests.",
          },
          {
            workload: "GRAPH_EXTRACTION",
            gatewayKey: "openai",
            gatewayProfileId: null,
            modelId: "gpt-5.4-mini",
            openAiReasoningEffort: null,
            source: "DEPLOYMENT_DEFAULT",
            editable: false,
            version: 0,
            lifecycleNote: "Deployment-managed. Changes affect only newly enqueued graph jobs and do not trigger reindexing.",
          },
        ]),
      })
    }
    if (
      url.pathname === "/api/admin/ai/routes/ASSISTANT_CHAT"
      && request.method() === "DELETE"
    ) {
      clearAssistantOverride()
      return route.fulfill({ status: 204 })
    }
    return route.fulfill({
      status: 404,
      contentType: "application/json",
      body: JSON.stringify({ message: `Unexpected ${request.method()} ${url.pathname}` }),
    })
  })
}
