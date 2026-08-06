import { expect, test, type Page, type Route } from "@playwright/test"

const ORGANIZATION_ID = "11111111-1111-4111-8111-111111111111"
const DEPARTMENT_ID = "22222222-2222-4222-8222-222222222222"
const USER_ID = "33333333-3333-4333-8333-333333333333"
const SPACE_ID = "44444444-4444-4444-8444-444444444444"

test("admin chooses a typed Space audience without seeing internal ids", async ({ page }, testInfo) => {
  const browserErrors: string[] = []
  const createBodies: unknown[] = []
  page.on("pageerror", (error) => browserErrors.push(error.message))
  page.on("console", (message) => {
    if (message.type() === "error") browserErrors.push(message.text())
  })

  await spacesHarness(page, createBodies)
  await page.setViewportSize({ width: 1365, height: 900 })
  await page.emulateMedia({ colorScheme: "dark" })
  await page.goto("/admin/spaces")

  await expect(page.getByRole("heading", { name: "Knowledge Spaces" })).toBeVisible()
  await expect(page.getByRole("radio", { name: /Organization/ })).toBeVisible()
  await expect(page.getByRole("radio", { name: /Department/ })).toHaveAttribute(
    "aria-checked",
    "true",
  )
  await expect(page.getByRole("radio", { name: /Restricted custom/ })).toBeVisible()
  await expect(page.getByText(DEPARTMENT_ID)).toHaveCount(0)
  await expect(page.getByText(ORGANIZATION_ID)).toHaveCount(0)
  await expect(page.getByText(SPACE_ID)).toHaveCount(0)

  await page.getByRole("button", { name: "Sales Knowledge" }).click()
  await expect(page.getByText("Managed by audience policy")).toBeVisible()
  await expect(page.getByText("Not effective · remove drift")).toBeVisible()
  await expect(page.getByRole("button", { name: /Remove ineffective Can read/ })).toBeVisible()
  await expect(page.getByText("Audience policy drift")).toBeVisible()
  await expect(page.getByRole("button", { name: /Revoke Can read/ })).toHaveCount(0)

  await page.getByLabel("Knowledge Space name").fill("Incident Response")
  await page.getByRole("radio", { name: /Restricted custom/ }).click()
  await expect(page.getByLabel("Owning department")).toHaveCount(0)
  await page.getByRole("button", { name: "Create Space" }).click()

  await expect(page.getByText("Incident Response was created")).toBeVisible()
  expect(createBodies).toEqual([
    { name: "Incident Response", audienceMode: "RESTRICTED_CUSTOM" },
  ])
  expect(browserErrors).toEqual([])

  const screenshot = testInfo.outputPath("typed-space-audiences.png")
  await page.screenshot({ path: screenshot, fullPage: true })
  await testInfo.attach("typed-space-audiences", {
    path: screenshot,
    contentType: "image/png",
  })
})

async function spacesHarness(page: Page, createBodies: unknown[]) {
  const spaces: Array<Record<string, unknown>> = [
    {
      id: SPACE_ID,
      key: "sales-knowledge",
      name: "Sales Knowledge",
      audienceMode: "DEPARTMENT",
      audienceVersion: 1,
      departmentId: DEPARTMENT_ID,
      active: true,
      grants: [
        {
          relation: "viewer",
          subject: `organizational_unit:${DEPARTMENT_ID}#member`,
          effective: true,
        },
        {
          relation: "viewer",
          subject: `organization:${ORGANIZATION_ID}#member`,
          effective: false,
        },
        { relation: "administrator", subject: `user:${USER_ID}`, effective: true },
      ],
      grantsComplete: true,
      policyVersion: "model-version-hidden",
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
        userId: USER_ID,
        organizationId: ORGANIZATION_ID,
        departmentId: DEPARTMENT_ID,
        clearance: "STANDARD",
        canManageMembers: true,
      })
    }
    if (url.pathname === "/api/session/csrf") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        headers: { "set-cookie": "XSRF-TOKEN=spaces-csrf; Path=/" },
        body: JSON.stringify({
          headerName: "X-XSRF-TOKEN",
          parameterName: "_csrf",
          token: "spaces-csrf",
        }),
      })
    }
    if (url.pathname === "/api/me") {
      return json(route, {
        userId: USER_ID,
        organizationId: ORGANIZATION_ID,
        name: "Organization Admin",
        email: "admin@example.test",
        departmentId: DEPARTMENT_ID,
        departmentName: "Sales",
        clearance: "STANDARD",
      })
    }
    if (url.pathname === "/api/organization/context") {
      return json(route, {
        organizationId: ORGANIZATION_ID,
        departments: [
          { id: DEPARTMENT_ID, organizationId: ORGANIZATION_ID, name: "Sales" },
        ],
        users: [],
      })
    }
    if (url.pathname === "/api/admin/users") return json(route, [])
    if (url.pathname === "/api/admin/roles") {
      return json(route, { roles: [], complete: true, policyVersion: "hidden" })
    }
    if (url.pathname === "/api/admin/knowledge-spaces/grant-options") {
      return json(route, [
        {
          relation: "viewer",
          kinds: ["DEPARTMENT", "USER"],
          roles: [],
        },
        {
          relation: "contributor",
          kinds: ["DEPARTMENT", "ROLE", "USER"],
          roles: ["knowledge-contributor"],
        },
        {
          relation: "reviewer",
          kinds: ["DEPARTMENT_MANAGERS", "ROLE", "USER"],
          roles: ["knowledge-reviewer"],
        },
        { relation: "administrator", kinds: ["USER"], roles: [] },
      ])
    }
    if (url.pathname === "/api/admin/knowledge-spaces" && request.method() === "GET") {
      return json(route, spaces)
    }
    if (url.pathname === "/api/admin/knowledge-spaces" && request.method() === "POST") {
      const body = request.postDataJSON()
      createBodies.push(body)
      const created = {
        id: "55555555-5555-4555-8555-555555555555",
        key: "incident-response",
        name: body.name,
        audienceMode: body.audienceMode,
        audienceVersion: 1,
        active: true,
        grants: [{ relation: "administrator", subject: `user:${USER_ID}`, effective: true }],
        grantsComplete: true,
        policyVersion: "model-version-hidden",
      }
      spaces.push(created)
      return json(route, created, 201)
    }

    return json(route, [])
  })
}

function json(
  route: Route,
  body: unknown,
  status = 200,
) {
  return route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) })
}
