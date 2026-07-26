import { expect, test } from "@playwright/test"

test("shows generic MCP onboarding for Claude, Codex, and compatible clients", async ({
  page,
}) => {
  const browserErrors: string[] = []
  page.on("pageerror", (error) => browserErrors.push(error.message))
  page.on("console", (message) => {
    if (message.type() === "error") browserErrors.push(message.text())
  })

  await page.route("**/api/session", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        authenticated: true,
        name: "Support Agent",
        email: "agent@example.test",
        userId: "66666666-6666-4666-8666-666666666666",
        organizationId: "11111111-1111-4111-8111-111111111111",
        departmentId: "33333333-3333-4333-8333-333333333333",
        role: "EMPLOYEE",
      }),
    }),
  )

  await page.goto("/connect")

  await expect(
    page.getByRole("heading", { name: "Connect AI clients" }),
  ).toBeVisible()
  await expect(page.getByText("https://om.kl3in.tech/mcp", { exact: true })).toBeVisible()
  await expect(page.getByText("Read only", { exact: true })).toBeVisible()

  await page.getByRole("tab", { name: "Codex" }).click()
  await expect(
    page.getByText(
      "codex mcp add orgmemory --url https://om.kl3in.tech/mcp --oauth-resource https://om.kl3in.tech/mcp",
      { exact: true },
    ),
  ).toBeVisible()
  await expect(
    page.getByText("codex mcp login orgmemory --scopes assets:read", {
      exact: true,
    }),
  ).toBeVisible()

  await page.getByRole("tab", { name: "Other clients" }).click()
  await expect(
    page.getByText("restricted Dynamic Client Registration", { exact: false }),
  ).toBeVisible()
  await expect(page.getByText("do not need a shared client secret", { exact: false })).toBeVisible()
  await expect(page.getByText("No mutations in this POC", { exact: true })).toBeVisible()
  expect(browserErrors).toEqual([])
})
