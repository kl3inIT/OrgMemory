import { expect, test, type Page, type Route } from "@playwright/test"

const ORGANIZATION_ID = "11111111-1111-4111-8111-111111111111"
const ADMIN_ID = "22222222-2222-4222-8222-222222222222"
const USER_ID = "33333333-3333-4333-8333-333333333333"
const SALES_ID = "44444444-4444-4444-8444-444444444444"
const ENGINEERING_ID = "55555555-5555-4555-8555-555555555555"

test("administrator sees and deliberately changes retrieval attributes", async ({ page }) => {
  const patches: unknown[] = []
  await usersHarness(page, patches)
  await page.setViewportSize({ width: 1440, height: 960 })
  await page.goto("/admin/users")

  await expect(page.getByRole("heading", { name: "Users" })).toBeVisible()
  await page.getByRole("button", { name: "Open account menu for Organization Admin" }).click()
  await expect(page.getByText("Sales · Standard clearance")).toBeVisible()
  await page.keyboard.press("Escape")
  await expect(page.getByText("Avery Analyst")).toBeVisible()

  await page.getByLabel("Clearance for Avery Analyst").click()
  await page.getByRole("option", { name: "Executive" }).click()
  await expect(page.getByRole("alertdialog")).toContainText(
    "org-wide access to CONFIDENTIAL and RESTRICTED evidence",
  )
  await expect(page.getByRole("alertdialog")).toContainText(
    "It does not grant administrative permissions",
  )
  await page.getByRole("button", { name: "Raise to Executive" }).click()

  await page.getByLabel("Department for Avery Analyst").click()
  await page.getByRole("option", { name: "Engineering" }).click()

  await expect.poll(() => patches).toEqual([
    { clearance: "EXECUTIVE" },
    { departmentId: ENGINEERING_ID },
  ])
})

async function usersHarness(page: Page, patches: unknown[]) {
  const departments = [
    { id: SALES_ID, organizationId: ORGANIZATION_ID, name: "Sales" },
    { id: ENGINEERING_ID, organizationId: ORGANIZATION_ID, name: "Engineering" },
  ]
  const users = [
    {
      id: ADMIN_ID,
      email: "admin@example.test",
      name: "Organization Admin",
      departmentId: SALES_ID,
      clearance: "STANDARD",
      active: true,
      signInLinked: true,
    },
    {
      id: USER_ID,
      email: "avery@example.test",
      name: "Avery Analyst",
      departmentId: SALES_ID,
      clearance: "STANDARD",
      active: true,
      signInLinked: true,
    },
  ]

  await page.route("**/api/**", async (route) => {
    const request = route.request()
    const url = new URL(request.url())

    if (url.pathname === "/api/session") {
      return json(route, {
        authenticated: true,
        name: "Organization Admin",
        email: "admin@example.test",
        userId: ADMIN_ID,
        organizationId: ORGANIZATION_ID,
        departmentId: SALES_ID,
        clearance: "STANDARD",
        canManageMembers: true,
      })
    }
    if (url.pathname === "/api/session/csrf") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        headers: { "set-cookie": "XSRF-TOKEN=users-csrf; Path=/" },
        body: JSON.stringify({
          headerName: "X-XSRF-TOKEN",
          parameterName: "_csrf",
          token: "users-csrf",
        }),
      })
    }
    if (url.pathname === "/api/me") {
      return json(route, {
        userId: ADMIN_ID,
        organizationId: ORGANIZATION_ID,
        name: "Organization Admin",
        email: "admin@example.test",
        departmentId: SALES_ID,
        departmentName: "Sales",
        clearance: "STANDARD",
      })
    }
    if (url.pathname === "/api/organization/context") {
      return json(route, { organizationId: ORGANIZATION_ID, departments, users })
    }
    if (url.pathname === "/api/admin/invitations") return json(route, [])
    if (url.pathname === "/api/admin/users" && request.method() === "GET") {
      return json(route, users)
    }
    if (url.pathname === `/api/admin/users/${USER_ID}` && request.method() === "PATCH") {
      const body = request.postDataJSON()
      patches.push(body)
      Object.assign(users[1], body)
      return json(route, users[1])
    }

    return json(route, [])
  })
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) })
}
