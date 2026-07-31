import { expect, test, type Page, type Route } from "@playwright/test"

const PACK_ID = "a1000000-0000-0000-0000-000000000001"
const PACK_RELEASE_ID = "a1000000-0000-0000-0000-000000000002"
const PACK_REVISION_ID = "a1000000-0000-0000-0000-000000000003"
const REVIEW_ID = "a1000000-0000-0000-0000-000000000004"
const OWNER_ID = "44444444-4444-4444-4444-444444444444"
const REVIEWER_ID = "55555555-5555-5555-5555-555555555555"
const SUPPORT_AGENT_ID = "66666666-6666-6666-6666-666666666666"
const BACKUP_OWNER_ID = "77777777-7777-7777-7777-777777777777"
const ORGANIZATION_ID = "11111111-1111-1111-1111-111111111111"
const DEPARTMENT_ID = "33333333-3333-3333-3333-333333333333"
const INSTRUCTION_ID = "a2000000-0000-0000-0000-000000000001"
const INSTRUCTION_RELEASE_ID = "a2000000-0000-0000-0000-000000000002"
const PROMPT_ID = "a3000000-0000-0000-0000-000000000001"
const PROMPT_RELEASE_ID = "a3000000-0000-0000-0000-000000000002"
const KNOWLEDGE_ID = "90000000-0000-0000-0000-000000000002"
const KNOWLEDGE_VERSION_ID = "90000000-0000-0000-0000-000000000007"
const SKILL_ID = "b1000000-0000-0000-0000-000000000001"
const SKILL_REVISION_ID = "b1000000-0000-0000-0000-000000000002"
const SKILL_RELEASE_ID = "b1000000-0000-0000-0000-000000000005"
const SKILL_DIGEST = "c".repeat(64)

test("Skill publication hands the author to capability-aware Governance", async ({
  page,
}) => {
  const harness = await skillGovernanceHarness(page)

  await page.goto(`/assets/${SKILL_ID}/governance`)
  await expect(page.getByRole("heading", { name: "Governance workspace" })).toBeVisible()
  await expect(page.getByRole("tab", { name: "Draft" })).toHaveAttribute(
    "data-state",
    "active",
  )
  await expect(page.getByText("Skill package", { exact: true })).toBeVisible()
  await expect(page.getByText(SKILL_DIGEST)).toBeVisible()
  await expect(page.getByText("references/policy.md")).toBeVisible()

  await expect(page.getByRole("tab", { name: "Review" })).toHaveCount(0)
  await page.getByLabel("Version").fill("1.0.0")
  await page.getByRole("button", { name: "Publish Skill" }).click()
  await page.getByRole("button", { name: "Confirm publish skill" }).click()

  await expect(page.getByRole("tab", { name: "Releases" })).toHaveAttribute(
    "data-state",
    "active",
  )
  await expect(page.getByText("Direct", { exact: true })).toBeVisible()
  await expect(page.getByText("1.0.0", { exact: true })).toBeVisible()
  expect(harness.requests).toContain(
    `GET /api/assets/${SKILL_ID}/governance-actions`,
  )
  expect(harness.requests).toContain(
    `POST /api/assets/${SKILL_ID}/skill-releases`,
  )
  expect(harness.unexpectedRequests).toEqual([])
  expect(harness.browserErrors).toEqual([])
})

test("authenticated Skill detail reads its install contract through the browser endpoint", async ({
  page,
}) => {
  const harness = await releasedSkillHarness(page)

  await page.goto(`/assets/${SKILL_ID}?release=${SKILL_RELEASE_ID}`)

  await expect(page.getByRole("heading", { name: "decision-record-writer" })).toBeVisible()
  await expect(page.getByText("Install this exact Skill")).toBeVisible()
  await expect(
    page.getByText(
      "orgmemory skill add productivity/decision-record-writer@1.0.0 --agent codex",
    ),
  ).toBeVisible()
  expect(harness.requests).toContain(
    `GET /api/assets/${SKILL_ID}/releases/${SKILL_RELEASE_ID}/skill-manifest`,
  )
  expect(
    harness.requests.some((request) => request.includes("/api/asset-delivery/")),
  ).toBe(false)
  expect(harness.unexpectedRequests).toEqual([])
  expect(harness.browserErrors).toEqual([])
})

test("two users prove governed release and second-user Pack completion", async ({
  browser,
  baseURL,
}) => {
  const ownerContext = await browser.newContext({ baseURL })
  const ownerPage = await ownerContext.newPage()
  const ownerHarness = await assetHarness(ownerPage, "owner")

  await ownerPage.goto(`/assets/${PACK_ID}/governance`)
  await expect(ownerPage.getByRole("heading", { name: "Governance workspace" })).toBeVisible()
  await ownerPage.getByRole("tab", { name: "Review" }).click()
  await expect(ownerPage.getByText("APPROVED", { exact: true })).toBeVisible()
  await expect(ownerPage.getByText("Approved by independent reviewer")).toBeVisible()
  await ownerPage.getByRole("tab", { name: "Releases" }).click()
  await expect(ownerPage.getByText("1.0.0", { exact: true })).toBeVisible()
  await expect(ownerPage.getByText("AVAILABLE", { exact: true })).toBeVisible()
  expect(ownerHarness.unexpectedRequests).toEqual([])
  expect(ownerHarness.browserErrors).toEqual([])
  await ownerContext.close()

  const supportContext = await browser.newContext({ baseURL })
  const supportPage = await supportContext.newPage()
  const supportHarness = await assetHarness(supportPage, "support")

  await supportPage.goto("/assets")
  await expect(supportPage.getByRole("heading", { name: "Assets" })).toBeVisible()
  await expect(supportPage.getByText("1 result", { exact: true })).toBeVisible()
  await expect(supportPage.getByText("Filtered by your live permissions")).toHaveCount(0)
  await expect(
    supportPage.getByText("Approved capability packs and reusable assets you can use now."),
  ).toHaveCount(0)
  await expect(
    supportPage.getByRole("link", { name: "L1 Customer Support Capability Onboarding" }),
  ).toBeVisible()
  await expect(supportPage.getByText("Restricted Security Prompt")).toHaveCount(0)
  const filterBarBox = await supportPage.locator('[data-slot="filter-bar"]').boundingBox()
  const assetGridBox = await supportPage
    .getByRole("region", { name: "Visible assets" })
    .boundingBox()
  expect(filterBarBox).not.toBeNull()
  expect(assetGridBox).not.toBeNull()
  expect(assetGridBox!.y - (filterBarBox!.y + filterBarBox!.height)).toBeGreaterThanOrEqual(16)
  await supportPage.getByRole("link", { name: "View pack" }).click()
  await expect(supportPage.getByText(`Owner: ${SUPPORT_AGENT_ID}`)).toHaveCount(0)
  await expect(supportPage.getByText(`Backup: ${BACKUP_OWNER_ID}`)).toHaveCount(0)
  await expect(supportPage.getByText("Support SLA and escalation")).toBeVisible()
  await supportPage.getByRole("link", { name: "Open pack" }).click()

  await expect(supportPage.getByRole("heading", { name: "L1 Customer Support Capability Onboarding" })).toBeVisible()
  await expect(supportPage.getByText("0%", { exact: true })).toBeVisible()
  await supportPage.getByRole("button", { name: "Mark complete: Support SLA and escalation" }).click()
  await expect(supportPage.getByText("33%", { exact: true })).toBeVisible()
  await supportPage.getByRole("button", { name: "Mark complete: Classify and respond" }).click()
  await expect(supportPage.getByText("67%", { exact: true })).toBeVisible()
  await supportPage.getByRole("button", { name: "Mark complete: Triage customer ticket" }).click()
  await expect(supportPage.getByText("100%", { exact: true })).toBeVisible()
  await expect(supportPage.getByText("COMPLETED", { exact: true })).toBeVisible()

  expect(supportHarness.unexpectedRequests).toEqual([])
  expect(supportHarness.browserErrors).toEqual([])
  expect(supportHarness.requests).toContain("GET /api/assets/catalog")
  expect(
    supportHarness.requests.filter((request) => request.startsWith("PUT /api/assets/")),
  ).toHaveLength(3)
  await supportContext.close()
})

test("asset catalog defaults to a grid and keeps list state in the URL", async ({
  page,
}) => {
  const harness = await assetHarness(page, "support", catalogRecommendations())

  await page.setViewportSize({ width: 1536, height: 1024 })
  await page.goto("/assets")

  await expect(page.getByText("18 results", { exact: true })).toBeVisible()
  await expect(page.getByRole("link", { name: "Add asset" })).toBeVisible()
  await expect(page.getByRole("tab", { name: "All Assets" })).toHaveAttribute(
    "data-state",
    "active",
  )
  await expect(page.getByRole("tab", { name: "My Assets" })).toBeVisible()
  await expect(page.getByRole("table")).toHaveCount(0)
  await expect(page.getByRole("region", { name: "Visible assets" })).toBeVisible()
  await expect(page.getByText("Showing 1–18 of 18", { exact: true })).toBeVisible()

  const searchBox = await page.locator('[data-slot="input-group"]').first().boundingBox()
  const scopeTabs = await page.getByRole("tablist", { name: "Asset scope" }).boundingBox()
  expect(searchBox).not.toBeNull()
  expect(scopeTabs).not.toBeNull()
  expect(Math.abs(searchBox!.y - scopeTabs!.y)).toBeLessThanOrEqual(2)

  await page.goto("/assets?page=2")
  await page.getByRole("tab", { name: "My Assets" }).click()
  await expect(page).toHaveURL(/scope=MINE/)
  await expect(page).not.toHaveURL(/page=2/)
  await expect(page.getByText("1 result", { exact: true })).toBeVisible()
  await expect(page.getByRole("link", { name: "Draft incident response skill" })).toBeVisible()
  await expect(page.getByText("Draft", { exact: true })).toBeVisible()
  expect(harness.ownedQueries.at(-1)).toMatchObject({
    page: "1",
    pageSize: "24",
    sort: "RECENTLY_UPDATED",
    q: null,
    type: null,
  })

  await page.getByRole("textbox", { name: "Search visible assets" }).fill("incident")
  await expect.poll(() => harness.ownedQueries.at(-1)?.q).toBe("incident")
  await page.getByRole("combobox", { name: "Filter assets by type" }).click()
  await page.getByRole("option", { name: "Skills" }).click()
  await expect.poll(() => harness.ownedQueries.at(-1)?.type).toBe("SKILL")

  await page.getByRole("textbox", { name: "Search visible assets" }).fill("")
  await page.getByRole("combobox", { name: "Filter assets by type" }).click()
  await page.getByRole("option", { name: "All types" }).click()
  await expect(page.getByRole("listbox")).toBeHidden()
  if (process.env.DESIGN_QA_CAPTURE) {
    await page.screenshot({
      path: "../output/design-qa/asset-catalog-mine.png",
      fullPage: false,
    })
  }
  await page.getByRole("tab", { name: "All Assets" }).click()
  await expect(page).not.toHaveURL(/scope=/)
  await expect(page.getByText("18 results", { exact: true })).toBeVisible()
  await expect(page.getByRole("tab", { name: "All Assets" })).toHaveAttribute(
    "data-state",
    "active",
  )

  if (process.env.DESIGN_QA_CAPTURE) {
    await page.screenshot({
      path: "../output/design-qa/asset-catalog-grid.png",
      fullPage: false,
    })
  }

  await page.getByRole("button", { name: "List view" }).click()
  await expect(page).toHaveURL(/view=LIST/)
  await expect(page.getByRole("table")).toBeVisible()

  await page.getByRole("button", { name: "Grid view" }).click()
  await expect(page).not.toHaveURL(/view=/)
  await expect(page.getByRole("table")).toHaveCount(0)
  await expect(page.getByRole("region", { name: "Visible assets" })).toBeVisible()

  await page.getByRole("link", { name: "Add asset" }).click()
  await expect(page).toHaveURL(/\/assets\/new$/)
  await expect(page.locator('[data-sidebar="menu-button"][href="/assets"]')).toHaveAttribute(
    "aria-current",
    "page",
  )
  await expect(page.getByRole("heading", { level: 1, name: "Add an asset" })).toBeVisible()
  await expect(page.getByRole("button", { name: /Skill/ })).toHaveAttribute(
    "aria-pressed",
    "true",
  )
  await expect(page.getByText("SKILL.md required", { exact: true })).toBeVisible()
  await expect(page.getByRole("button", { name: "Continue" })).toHaveCount(0)

  if (process.env.DESIGN_QA_CAPTURE) {
    await page.screenshot({
      path: "../output/design-qa/asset-add-entry.png",
      fullPage: false,
    })
  }

  await page.getByRole("button", { name: /Prompt template/ }).click()
  await expect(page.getByRole("button", { name: /Prompt template/ })).toHaveAttribute(
    "aria-pressed",
    "true",
  )
  await expect(
    page.getByText("Browser authoring for this Asset type is not available yet."),
  ).toBeVisible()

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole("heading", { level: 1, name: "Add an asset" })).toBeVisible()
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBe(true)

  await page.getByRole("link", { name: "Back to Assets" }).click()
  await expect(page).toHaveURL(/\/assets$/)

  expect(harness.unexpectedRequests).toEqual([])
  expect(harness.browserErrors).toEqual([])
})

test("asset navigation stacks without horizontal page overflow", async ({ page }) => {
  const harness = await assetHarness(page, "support", catalogRecommendations().slice(0, 2))

  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto("/assets")

  await expect(page.getByRole("tab", { name: "All Assets" })).toBeVisible()
  await expect(page.getByRole("combobox", { name: "Filter assets by type" })).toBeVisible()
  const searchBox = await page.locator('[data-slot="input-group"]').first().boundingBox()
  const scopeTabs = await page.getByRole("tablist", { name: "Asset scope" }).boundingBox()
  expect(searchBox).not.toBeNull()
  expect(scopeTabs).not.toBeNull()
  expect(scopeTabs!.y).toBeGreaterThan(searchBox!.y + searchBox!.height)
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth),
  ).toBeLessThanOrEqual(await page.evaluate(() => document.documentElement.clientWidth))

  if (process.env.DESIGN_QA_CAPTURE) {
    await page.screenshot({
      path: "../output/design-qa/asset-catalog-mobile.png",
      fullPage: false,
    })
  }

  expect(harness.unexpectedRequests).toEqual([])
  expect(harness.browserErrors).toEqual([])
})

async function assetHarness(
  page: Page,
  actor: "owner" | "support",
  catalogItems = [supportPackRecommendation()],
) {
  const harness = baseHarness(page, actor, "golden-poc-token")
  const completed = new Set<string>()
  const ownedQueries: Array<Record<string, string | null>> = []

  await page.route("**/api/**", async (route) => {
    const requestContext = await harness.beginRoute(route)
    if (!requestContext) return
    const { request, url, signature } = requestContext
    if (
      actor === "support" &&
      url.pathname === "/api/assets/catalog"
    ) {
      await json(route, {
        items: catalogItems,
        total: catalogItems.length,
        page: 1,
        pageSize: 24,
        totalPages: 1,
        sort: "RECENTLY_RELEASED",
      })
      return
    }
    if (url.pathname === "/api/assets/owned") {
      const requestedPage = Number(url.searchParams.get("page") ?? "1")
      ownedQueries.push({
        page: url.searchParams.get("page"),
        pageSize: url.searchParams.get("pageSize"),
        sort: url.searchParams.get("sort"),
        q: url.searchParams.get("q"),
        type: url.searchParams.get("type"),
      })
      await json(route, {
        items: requestedPage === 1 ? [ownedDraftSummary()] : [],
        total: 1,
        page: requestedPage,
        pageSize: 24,
        totalPages: 1,
        sort: url.searchParams.get("sort") ?? "RECENTLY_UPDATED",
      })
      return
    }
    if (url.pathname === `/api/assets/${PACK_ID}`) {
      await json(route, packAsset())
      return
    }
    if (url.pathname === `/api/assets/${PACK_ID}/governance-actions`) {
      await json(route, {
        canSubmitReview: true,
        canReview: true,
        canPublish: true,
        canWithdraw: true,
      })
      return
    }
    if (
      actor === "support" &&
      url.pathname ===
        `/api/assets/${PACK_ID}/releases/${PACK_RELEASE_ID}/pack-definition`
    ) {
      await json(route, packDefinition())
      return
    }
    if (
      actor === "support" &&
      url.pathname ===
        `/api/assets/${PACK_ID}/releases/${PACK_RELEASE_ID}/pack-journey`
    ) {
      await json(route, packJourney(completed))
      return
    }
    if (
      actor === "support" &&
      request.method() === "PUT" &&
      url.pathname.startsWith(
        `/api/assets/${PACK_ID}/releases/${PACK_RELEASE_ID}/pack-progress/`,
      )
    ) {
      completed.add(url.pathname.split("/").at(-1)!)
      await json(route, packJourney(completed))
      return
    }

    harness.unexpectedRequests.push(signature)
    await json(route, { message: "Unexpected golden POC request" }, 500)
  })

  return { ...harness.result, ownedQueries }
}

function supportPackRecommendation() {
  return {
    assetId: PACK_ID,
    type: "CAPABILITY_PACK",
    namespace: "support",
    slug: "l1-onboarding",
    title: "L1 Customer Support Capability Onboarding",
    summary: "Complete the first correct L1 support ticket",
    knowledgeSpaceId: "88888888-8888-4888-8888-888888888802",
    portfolioState: "ACTIVE",
    releaseId: PACK_RELEASE_ID,
    versionLabel: "1.0.0",
    releaseDigest: "a".repeat(64),
    availability: "AVAILABLE",
    releasedAt: "2026-07-28T00:00:00Z",
  }
}

function ownedDraftSummary() {
  return {
    id: SKILL_ID,
    type: "SKILL",
    namespace: "engineering",
    slug: "incident-response",
    title: "Draft incident response skill",
    summary: "A reusable incident response procedure being prepared for release.",
    knowledgeSpaceId: "88888888-8888-4888-8888-888888888802",
    portfolioState: "DRAFT_ONLY",
    updatedAt: "2026-07-31T12:00:00Z",
  }
}

function catalogRecommendations() {
  const types = [
    "CAPABILITY_PACK",
    "WORK_INSTRUCTION",
    "PROMPT_TEMPLATE",
    "SKILL",
  ] as const
  const titles = [
    "Executive Strategy Review",
    "Review Strategic Priorities",
    "Executive Strategy Brief",
    "Quarterly Planning Assistant",
    "Engineering Incident Response",
    "Respond to an Engineering Incident",
    "Engineering Incident Triage",
    "Production Readiness Checklist",
    "Expense Claim Onboarding",
    "Submit a Travel Expense Claim",
    "Expense Claim Review",
    "Finance Policy Skill",
    "New Employee Onboarding",
    "Complete Employee Onboarding",
    "Employee Onboarding Answer",
    "People Operations Skill",
    "Customer Support Onboarding",
    "Escalate a Support Incident",
  ]

  return titles.map((title, index) => {
    const number = String(index + 10).padStart(12, "0")
    const type = types[index % types.length]
    const namespace = ["executive", "engineering", "finance", "people"][index % 4]
    return {
      assetId: `a9000000-0000-4000-8000-${number}`,
      type,
      namespace,
      slug: title.toLowerCase().replaceAll(" ", "-"),
      title,
      summary: "Approved reusable guidance for a common company workflow.",
      knowledgeSpaceId: "88888888-8888-4888-8888-888888888802",
      portfolioState: "ACTIVE",
      releaseId: `b9000000-0000-4000-8000-${number}`,
      versionLabel: "1.0.0",
      releaseDigest: String(index).padStart(64, "0"),
      availability: "AVAILABLE",
      releasedAt: new Date(Date.UTC(2026, 6, 28 - index)).toISOString(),
    }
  })
}

async function skillGovernanceHarness(page: Page) {
  const harness = baseHarness(page, "owner", "skill-governance-token")
  let published = false

  await page.route("**/api/**", async (route) => {
    const requestContext = await harness.beginRoute(route)
    if (!requestContext) return
    const { request, url, signature } = requestContext
    if (url.pathname === `/api/assets/${SKILL_ID}`) {
      await json(route, skillDraftAsset(published))
      return
    }
    if (url.pathname === `/api/assets/${SKILL_ID}/governance-actions`) {
      await json(route, {
        canSubmitReview: true,
        canReview: false,
        canPublish: false,
        canPublishSkill: true,
        canWithdraw: false,
      })
      return
    }
    if (
      request.method() === "POST" &&
      url.pathname === `/api/assets/${SKILL_ID}/skill-releases`
    ) {
      published = true
      await json(route, skillDraftAsset(true))
      return
    }

    harness.unexpectedRequests.push(signature)
    await json(route, { message: "Unexpected Skill Governance request" }, 500)
  })

  return harness.result
}

async function releasedSkillHarness(page: Page) {
  const harness = baseHarness(page, "owner", "released-skill-token")

  await page.route("**/api/**", async (route) => {
    const requestContext = await harness.beginRoute(route)
    if (!requestContext) return
    const { url, signature } = requestContext
    if (url.pathname === `/api/assets/${SKILL_ID}`) {
      await json(route, releasedSkillAsset())
      return
    }
    if (
      url.pathname ===
      `/api/assets/${SKILL_ID}/releases/${SKILL_RELEASE_ID}/skill-manifest`
    ) {
      await json(route, skillInstallManifest())
      return
    }

    harness.unexpectedRequests.push(signature)
    await json(route, { message: "Unexpected released Skill request" }, 500)
  })

  return harness.result
}

function baseHarness(
  page: Page,
  actor: "owner" | "support",
  csrfToken: string,
) {
  const requests: string[] = []
  const unexpectedRequests: string[] = []
  const browserErrors: string[] = []

  page.on("pageerror", (error) => browserErrors.push(error.message))
  page.on("console", (message) => {
    if (message.type() === "error") browserErrors.push(message.text())
  })

  return {
    unexpectedRequests,
    result: { requests, unexpectedRequests, browserErrors },
    async beginRoute(route: Route) {
      const request = route.request()
      const url = new URL(request.url())
      const signature = `${request.method()} ${url.pathname}`
      requests.push(signature)

      if (url.pathname === "/api/session") {
        await json(route, session(actor))
        return undefined
      }
      if (url.pathname === "/api/session/csrf") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          headers: { "set-cookie": `XSRF-TOKEN=${csrfToken}; Path=/` },
          body: JSON.stringify({
            headerName: "X-XSRF-TOKEN",
            parameterName: "_csrf",
            token: csrfToken,
          }),
        })
        return undefined
      }

      return { request, url, signature }
    },
  }
}

function session(actor: "owner" | "support") {
  return {
    authenticated: true,
    name: actor === "owner" ? "Operations Lead" : "Support Agent",
    email: actor === "owner" ? "lead@example.test" : "agent@example.test",
    userId: actor === "owner" ? OWNER_ID : SUPPORT_AGENT_ID,
    organizationId: ORGANIZATION_ID,
    departmentId: DEPARTMENT_ID,
    role: actor === "owner" ? "MANAGER" : "EMPLOYEE",
  }
}

function packAsset() {
  const payload = JSON.stringify({
    purpose: "ROLE_ONBOARDING",
    audience: "L1 support agent",
    prerequisites: ["Active support account"],
    expectedOutcome: "Complete the first correct L1 support ticket",
    items: [
      { key: "knowledge", required: true, kind: "KNOWLEDGE_VERSION" },
      { key: "instruction", required: true, kind: "REGISTRY_RELEASE" },
      { key: "prompt", required: true, kind: "REGISTRY_RELEASE" },
    ],
    completionCriteria: ["Required items complete"],
  })
  const timestamp = "2026-07-26T00:00:00Z"
  return {
    id: PACK_ID,
    type: "CAPABILITY_PACK",
    namespace: "support",
    slug: "l1-onboarding",
    knowledgeSpaceId: "88888888-8888-4888-8888-888888888802",
    portfolioState: "ACTIVE",
    authorizationReady: true,
    draft: {
      id: "a1000000-0000-0000-0000-000000000005",
      lockVersion: 1,
      title: "L1 Customer Support Capability Onboarding",
      summary: "Complete the first correct L1 support ticket",
      classification: "INTERNAL",
      schemaVersion: "1",
      payload,
      editedByUserId: OWNER_ID,
      updatedAt: timestamp,
    },
    revisions: [
      {
        id: PACK_REVISION_ID,
        sequence: 1,
        title: "L1 Customer Support Capability Onboarding",
        summary: "Complete the first correct L1 support ticket",
        classification: "INTERNAL",
        schemaVersion: "1",
        payload,
        digest: "a".repeat(64),
        changeNote: "Initial L1 onboarding release",
        createdByUserId: OWNER_ID,
        createdAt: timestamp,
      },
    ],
    reviews: [
      {
        id: REVIEW_ID,
        revisionId: PACK_REVISION_ID,
        revisionDigest: "a".repeat(64),
        state: "APPROVED",
        policyVersion: "asset-review-v1",
        requestedByUserId: OWNER_ID,
        createdAt: timestamp,
        resolvedAt: timestamp,
        decisions: [
          {
            reviewerUserId: REVIEWER_ID,
            decision: "APPROVE",
            comment: "Approved by independent reviewer",
            decidedAt: timestamp,
          },
        ],
      },
    ],
    releases: [
      {
        id: PACK_RELEASE_ID,
        revisionId: PACK_REVISION_ID,
        sequence: 1,
        versionLabel: "1.0.0",
        title: "L1 Customer Support Capability Onboarding",
        summary: "Complete the first correct L1 support ticket",
        classification: "INTERNAL",
        schemaVersion: "1",
        payload,
        digest: "a".repeat(64),
        releasedByUserId: OWNER_ID,
        releasedAt: timestamp,
        availability: "AVAILABLE",
        availabilityHistory: [
          {
            availability: "AVAILABLE",
            reason: "Initial release",
            changedByUserId: OWNER_ID,
            effectiveAt: timestamp,
          },
        ],
      },
    ],
    ownershipHealth: {
      ownerPresent: true,
      backupOwnerPresent: true,
      orphaned: false,
      continuityAtRisk: false,
    },
    roleAssignments: [
      roleAssignment(SUPPORT_AGENT_ID, "OWNER"),
      roleAssignment(BACKUP_OWNER_ID, "BACKUP_OWNER"),
    ],
  }
}

function skillDraftAsset(published: boolean) {
  const timestamp = "2026-07-27T00:00:00Z"
  const payload = JSON.stringify({
    name: "expense-review",
    description: "Review one expense using approved policy",
    compatibility: "Claude Code and Codex",
    allowedTools: "Read",
    artifact: {
      sha256: SKILL_DIGEST,
      contentLength: 2048,
      mediaType: "application/zip",
    },
    files: [
      { path: "SKILL.md", size: 512, sha256: "d".repeat(64) },
      { path: "references/policy.md", size: 1024, sha256: "e".repeat(64) },
      { path: "scripts/check.js", size: 256, sha256: "f".repeat(64) },
    ],
  })
  return {
    id: SKILL_ID,
    type: "SKILL",
    namespace: "finance",
    slug: "expense-review",
    knowledgeSpaceId: "88888888-8888-4888-8888-888888888802",
    portfolioState: published ? "ACTIVE" : "DRAFT_ONLY",
    authorizationReady: true,
    draft: {
      id: "b1000000-0000-0000-0000-000000000004",
      lockVersion: 0,
      title: "Expense review",
      summary: "Review one expense using approved policy",
      classification: "INTERNAL",
      schemaVersion: "agent-skill.v1",
      payload,
      editedByUserId: OWNER_ID,
      updatedAt: timestamp,
    },
    revisions: published
      ? [
          {
            id: SKILL_REVISION_ID,
            sequence: 1,
            title: "Expense review",
            summary: "Review one expense using approved policy",
            classification: "INTERNAL",
            schemaVersion: "agent-skill.v1",
            payload,
            digest: SKILL_DIGEST,
            changeNote: "Direct Skill publication",
            createdByUserId: OWNER_ID,
            createdAt: timestamp,
          },
        ]
      : [],
    reviews: [],
    releases: published
      ? [
          {
            id: SKILL_RELEASE_ID,
            revisionId: SKILL_REVISION_ID,
            sequence: 1,
            versionLabel: "1.0.0",
            publicationMode: "DIRECT",
            title: "Expense review",
            summary: "Review one expense using approved policy",
            classification: "INTERNAL",
            schemaVersion: "agent-skill.v1",
            payload,
            digest: SKILL_DIGEST,
            releasedByUserId: OWNER_ID,
            releasedAt: timestamp,
            availability: "AVAILABLE",
            availabilityHistory: [
              {
                availability: "AVAILABLE",
                reason: "Direct Skill publication",
                changedByUserId: OWNER_ID,
                effectiveAt: timestamp,
              },
            ],
          },
        ]
      : [],
    ownershipHealth: {
      ownerPresent: true,
      backupOwnerPresent: true,
      orphaned: false,
      continuityAtRisk: false,
    },
    roleAssignments: [
      roleAssignment(OWNER_ID, "OWNER"),
      roleAssignment(BACKUP_OWNER_ID, "BACKUP_OWNER"),
    ],
  }
}

function releasedSkillAsset() {
  const asset = skillDraftAsset(true)
  const releasedAt = "2026-07-28T00:00:00Z"
  return {
    ...asset,
    namespace: "productivity",
    slug: "decision-record-writer",
    releases: [
      {
        id: SKILL_RELEASE_ID,
        revisionId: SKILL_REVISION_ID,
        sequence: 1,
        versionLabel: "1.0.0",
        publicationMode: "DIRECT",
        title: "decision-record-writer",
        summary: "Turn a completed discussion into a concise decision record",
        classification: "INTERNAL",
        schemaVersion: "agent-skill.v1",
        payload: asset.revisions[0]!.payload,
        digest: SKILL_DIGEST,
        releasedByUserId: OWNER_ID,
        releasedAt,
        availability: "AVAILABLE",
        availabilityHistory: [
          {
            availability: "AVAILABLE",
            reason: "Initial release",
            changedByUserId: OWNER_ID,
            effectiveAt: releasedAt,
          },
        ],
      },
    ],
  }
}

function skillInstallManifest() {
  return {
    assetId: SKILL_ID,
    releaseId: SKILL_RELEASE_ID,
    namespace: "productivity",
    slug: "decision-record-writer",
    coordinate: "productivity/decision-record-writer",
    version: "1.0.0",
    publicationMode: "DIRECT",
    title: "decision-record-writer",
    description: "Turn a completed discussion into a concise decision record",
    releaseDigest: SKILL_DIGEST,
    packageDigest: "d".repeat(64),
    packageLength: 1040,
    mediaType: "application/zip",
    license: "MIT",
    compatibility: "Claude Code and Codex",
    allowedTools: "Read",
    metadata: {},
    files: [
      {
        path: "SKILL.md",
        size: 512,
        sha256: "e".repeat(64),
      },
    ],
  }
}

function roleAssignment(principalId: string, role: "OWNER" | "BACKUP_OWNER") {
  return {
    id: role === "OWNER"
      ? "a5000000-0000-0000-0000-000000000001"
      : "a5000000-0000-0000-0000-000000000002",
    principalType: "user",
    principalId,
    role,
    validFrom: "2026-07-26T00:00:00Z",
    assignedByUserId: OWNER_ID,
    projectedAt: "2026-07-26T00:00:01Z",
  }
}

function packJourney(completed: Set<string>) {
  const items = [
    {
      key: "knowledge",
      required: true,
      order: 1,
      kind: "KNOWLEDGE",
      resourceId: KNOWLEDGE_ID,
      pinnedVersionId: KNOWLEDGE_VERSION_ID,
      title: "Support SLA and escalation",
      versionLabel: "1",
      availability: "AVAILABLE",
      completed: completed.has("knowledge"),
    },
    {
      key: "instruction",
      required: true,
      order: 2,
      kind: "REGISTRY_RELEASE",
      resourceId: INSTRUCTION_ID,
      pinnedVersionId: INSTRUCTION_RELEASE_ID,
      title: "Classify and respond",
      versionLabel: "1.0.0",
      availability: "AVAILABLE",
      completed: completed.has("instruction"),
    },
    {
      key: "prompt",
      required: true,
      order: 3,
      kind: "REGISTRY_RELEASE",
      resourceId: PROMPT_ID,
      pinnedVersionId: PROMPT_RELEASE_ID,
      title: "Triage customer ticket",
      versionLabel: "1.0.0",
      availability: "AVAILABLE",
      completed: completed.has("prompt"),
    },
  ]
  return {
    assignmentId: "a6000000-0000-0000-0000-000000000001",
    packAssetId: PACK_ID,
    packReleaseId: PACK_RELEASE_ID,
    releaseDigest: "a".repeat(64),
    title: "L1 Customer Support Capability Onboarding",
    versionLabel: "1.0.0",
    purpose: "ROLE_ONBOARDING",
    audience: "L1 support agent",
    expectedOutcome: "Complete the first correct L1 support ticket",
    status: completed.size === items.length ? "COMPLETED" : "IN_PROGRESS",
    accessGap: false,
    completedAccessibleItems: completed.size,
    items,
    startedAt: "2026-07-26T00:00:00Z",
    completedAt:
      completed.size === items.length ? "2026-07-26T00:05:00Z" : undefined,
  }
}

function packDefinition() {
  const journey = packJourney(new Set())
  return {
    packAssetId: PACK_ID,
    packReleaseId: PACK_RELEASE_ID,
    releaseDigest: "a".repeat(64),
    title: journey.title,
    versionLabel: journey.versionLabel,
    purpose: journey.purpose,
    audience: journey.audience,
    prerequisites: ["Active support account"],
    expectedOutcome: journey.expectedOutcome,
    completionCriteria: ["Required items complete"],
    reviewDate: "2026-08-26",
    owner: "Support Operations",
    accessGap: false,
    items: journey.items.map(({ completed: _completed, ...item }) => item),
  }
}

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  })
}
