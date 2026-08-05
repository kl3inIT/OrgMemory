import { expect, test, type Page, type Route } from "@playwright/test"

const ORGANIZATION_ID = "11111111-1111-4111-8111-111111111111"
const USER_ID = "22222222-2222-4222-8222-222222222222"
const SPACE_ID = "33333333-3333-4333-8333-333333333333"

test("graph explorer keeps its title readable beside a wrapping desktop toolbar", async ({
  page,
}) => {
  const browserErrors: string[] = []
  page.on("pageerror", (error) => browserErrors.push(error.message))
  page.on("console", (message) => {
    if (message.type() === "error") browserErrors.push(message.text())
  })

  await graphHarness(page)
  await page.setViewportSize({ width: 1459, height: 816 })
  await page.emulateMedia({ colorScheme: "dark" })
  await page.goto("/sources?view=graph")

  const heading = page.getByRole("heading", { level: 1, name: "Knowledge graph" })
  const actions = page.locator('[data-slot="page-header-actions"]')

  await expect(heading).toBeVisible()
  await expect(page.getByRole("button", { name: "Explore" })).toBeVisible()
  await expect(page.getByLabel("Knowledge space")).toContainText("Company Knowledge")

  const headingMetrics = await heading.evaluate((element) => {
    const rect = element.getBoundingClientRect()
    return {
      height: rect.height,
      width: rect.width,
      lineHeight: Number.parseFloat(window.getComputedStyle(element).lineHeight),
    }
  })
  expect(headingMetrics.width).toBeGreaterThan(140)
  expect(headingMetrics.height).toBeLessThanOrEqual(headingMetrics.lineHeight * 1.25)

  const [headingBox, actionsBox] = await Promise.all([heading.boundingBox(), actions.boundingBox()])
  expect(headingBox).not.toBeNull()
  expect(actionsBox).not.toBeNull()
  if (headingBox && actionsBox) {
    const overlaps =
      headingBox.x < actionsBox.x + actionsBox.width &&
      headingBox.x + headingBox.width > actionsBox.x &&
      headingBox.y < actionsBox.y + actionsBox.height &&
      headingBox.y + headingBox.height > actionsBox.y
    expect(overlaps).toBe(false)
  }

  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBe(true)
  expect(browserErrors).toEqual([])
})

async function graphHarness(page: Page) {
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url())

    if (url.pathname === "/api/session") {
      return json(route, {
        authenticated: true,
        name: "Employee",
        email: "employee@example.test",
        userId: USER_ID,
        organizationId: ORGANIZATION_ID,
        departmentId: null,
        role: "EMPLOYEE",
      })
    }
    if (url.pathname === "/api/session/csrf") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        headers: { "set-cookie": "XSRF-TOKEN=graph-layout-csrf; Path=/" },
        body: JSON.stringify({
          headerName: "X-XSRF-TOKEN",
          parameterName: "_csrf",
          token: "graph-layout-csrf",
        }),
      })
    }
    if (url.pathname === "/api/sources") return json(route, [])
    if (url.pathname === "/api/knowledge-spaces/upload-targets") return json(route, [])
    if (url.pathname === "/api/knowledge-spaces/visible") {
      return json(route, [{ id: SPACE_ID, key: "company", name: "Company Knowledge" }])
    }
    if (url.pathname === `/api/knowledge-spaces/${SPACE_ID}/graph/explorer`) {
      return json(route, {
        knowledgeSpaceId: SPACE_ID,
        authorizationGeneration: 7,
        canCurate: false,
        entities: [],
        relations: [],
        truncated: false,
      })
    }

    return json(route, [])
  })
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) })
}
