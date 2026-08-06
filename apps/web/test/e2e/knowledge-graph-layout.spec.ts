import { expect, test, type Page, type Route } from "@playwright/test"

const ORGANIZATION_ID = "11111111-1111-4111-8111-111111111111"
const USER_ID = "22222222-2222-4222-8222-222222222222"
const SPACE_ID = "33333333-3333-4333-8333-333333333333"
const ALLOWANCE_ENTITY_ID = "44444444-4444-4444-8444-444444444444"
const POLICY_ENTITY_ID = "55555555-5555-4555-8555-555555555555"
const CITATION_ID = "66666666-6666-4666-8666-666666666666"

test("graph explorer keeps its title readable beside a wrapping desktop toolbar", async ({
  page,
}, testInfo) => {
  const browserErrors: string[] = []
  const graphQueries: string[] = []
  page.on("pageerror", (error) => browserErrors.push(error.message))
  page.on("console", (message) => {
    if (message.type() === "error") browserErrors.push(message.text())
  })

  await graphHarness(page, graphQueries)
  await page.setViewportSize({ width: 1459, height: 816 })
  await page.emulateMedia({ colorScheme: "dark" })
  await page.goto("/sources?view=graph")

  const heading = page.getByRole("heading", { level: 1, name: "Knowledge graph" })
  const actions = page.locator('[data-slot="page-header-actions"]')

  await expect(heading).toBeVisible({ timeout: 20_000 })
  await expect(page.getByRole("button", { name: "Explore" })).toHaveCount(0)
  await expect(page.getByLabel("Knowledge space")).toContainText("Company Knowledge")
  await page.getByRole("textbox", { name: "Find an entity or relation" }).fill("allowance")
  await expect.poll(() => graphQueries).toContain("allowance")

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

  await page.getByRole("textbox", { name: "Search visible graph nodes" }).fill("300.000")
  await page.getByRole("button", { name: /300\.000 vnd mỗi ngày/ }).click()
  await expect(page.getByRole("heading", { level: 2, name: "300.000 vnd mỗi ngày" })).toBeVisible()
  await expect(page.getByText("Mức phụ cấp ăn uống 300.000 VND cho mỗi ngày.")).toBeVisible()
  await expect(page.getByRole("button", { name: "Expand neighbors" })).toBeVisible()
  await expect(page.getByRole("button", { name: "Hide" })).toBeVisible()
  await expect(page.getByText("Có mức phụ cấp", { exact: true })).toHaveCount(2)
  await expect(page.getByText("Outgoing · Áp dụng khi không có hóa đơn", { exact: true })).toBeVisible()
  await expect(page.getByText("Outgoing · Thuộc chính sách", { exact: true })).toBeVisible()
  await expect(page.getByText("Domestic travel policy", { exact: true })).toBeVisible()
  await expect(page.getByText("Meal allowance", { exact: true })).toBeVisible()
  await expect(page.getByText("Source 1", { exact: true })).toHaveCount(0)
  if (process.env.DESIGN_QA_CAPTURE) {
    const screenshot = testInfo.outputPath("knowledge-graph-inspector.png")
    await page.screenshot({ path: screenshot, fullPage: false })
    await testInfo.attach("knowledge-graph-inspector", {
      path: screenshot,
      contentType: "image/png",
    })
  }

  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBe(true)
  expect(browserErrors).toEqual([])
})

async function graphHarness(page: Page, graphQueries: string[]) {
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
        clearance: "STANDARD",
        canManageMembers: false,
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
    if (url.pathname === "/api/me") {
      return json(route, {
        userId: USER_ID,
        organizationId: ORGANIZATION_ID,
        name: "Employee",
        email: "employee@example.test",
        departmentId: null,
        departmentName: null,
        clearance: "STANDARD",
      })
    }
    if (url.pathname === "/api/sources") {
      return json(route, {
        items: [],
        nextCursor: null,
        pageSize: 25,
        total: 0,
        statusCounts: { processing: 0, ready: 0, attention: 0 },
      })
    }
    if (url.pathname === "/api/knowledge-spaces/upload-targets") return json(route, [])
    if (url.pathname === "/api/knowledge-spaces/visible") {
      return json(route, [{ id: SPACE_ID, key: "company", name: "Company Knowledge" }])
    }
    if (url.pathname === `/api/knowledge-spaces/${SPACE_ID}/graph/explorer`) {
      graphQueries.push(url.searchParams.get("q") ?? "")
      return json(route, {
        knowledgeSpaceId: SPACE_ID,
        authorizationGeneration: 7,
        canCurate: false,
        entities: [
          {
            id: ALLOWANCE_ENTITY_ID,
            name: "300.000 vnd mỗi ngày",
            type: "CONCEPT",
            description: "Mức phụ cấp ăn uống 300.000 VND cho mỗi ngày.",
            citationChunkIds: [CITATION_ID],
          },
          {
            id: POLICY_ENTITY_ID,
            name: "Có mức phụ cấp",
            type: "POLICY",
            description: "Phụ cấp ăn uống khi công tác trong nước.",
            citationChunkIds: [CITATION_ID],
          },
        ],
        relations: [
          {
            id: "77777777-7777-4777-8777-777777777777",
            sourceEntityId: ALLOWANCE_ENTITY_ID,
            targetEntityId: POLICY_ENTITY_ID,
            type: "ÁP_DỤNG_KHI_KHÔNG_CÓ_HÓA_ĐƠN",
            description: "Áp dụng khi không có hóa đơn tiếp khách.",
            weight: 1,
            citationChunkIds: [CITATION_ID],
          },
          {
            id: "88888888-8888-4888-8888-888888888888",
            sourceEntityId: ALLOWANCE_ENTITY_ID,
            targetEntityId: POLICY_ENTITY_ID,
            type: "THUỘC_CHÍNH_SÁCH",
            description: "Khoản phụ cấp thuộc chính sách công tác trong nước.",
            weight: 1,
            citationChunkIds: [CITATION_ID],
          },
        ],
        truncated: false,
      })
    }
    if (url.pathname === `/api/citations/${CITATION_ID}/excerpt`) {
      return json(route, {
        title: "Domestic travel policy",
        heading: "Meal allowance",
        excerpt: "The daily meal allowance is 300.000 VND.",
        presentationKind: "MARKDOWN",
      })
    }

    return json(route, [])
  })
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) })
}
