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
        clearance: "STANDARD",
        canManageMembers: false,
      }),
    }),
  )
  await page.route("**/api/me", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        userId: "66666666-6666-4666-8666-666666666666",
        organizationId: "11111111-1111-4111-8111-111111111111",
        name: "Support Agent",
        email: "agent@example.test",
        departmentId: "33333333-3333-4333-8333-333333333333",
        departmentName: "Customer Support",
        clearance: "STANDARD",
      }),
    }),
  )

  await page.goto("/connect")

  await expect(
    page.getByRole("heading", { name: "Connect AI clients" }),
  ).toBeVisible()
  await expect(page.getByText("https://om.kl3in.tech/mcp", { exact: true })).toBeVisible()
  await expect(
    page.getByText(
      "Read-only Streamable HTTP with OAuth 2.1 for Claude, Codex, and compatible MCP clients.",
      { exact: true },
    ),
  ).toBeVisible()
  await expect(page.getByText("Available", { exact: true })).toHaveCount(0)
  await expect(page.getByText("What the assistant can do", { exact: true })).toHaveCount(0)
  await expect(
    page.getByText("OrgMemory checks your current permissions on every request.", {
      exact: false,
    }),
  ).toBeVisible()
  await expect(
    page.getByText("cannot publish or modify them.", { exact: false }),
  ).toBeVisible()

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
  expect(browserErrors).toEqual([])
})
