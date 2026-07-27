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
const SKILL_REVIEW_ID = "b1000000-0000-0000-0000-000000000003"
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

  await page.getByLabel("Change note").fill("Ready for governed review")
  await page.getByRole("button", { name: "Submit for review" }).click()
  await page.getByRole("button", { name: "Confirm submit for review" }).click()

  await expect(page.getByRole("tab", { name: "Review" })).toHaveAttribute(
    "data-state",
    "active",
  )
  await expect(page.getByText("IN_REVIEW", { exact: true })).toBeVisible()
  await expect(page.getByRole("button", { name: "Approve exact digest" })).toHaveCount(0)
  expect(harness.requests).toContain(
    `GET /api/assets/${SKILL_ID}/governance-actions`,
  )
  expect(harness.requests).toContain(
    `POST /api/assets/${SKILL_ID}/submissions`,
  )
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
  await expect(supportPage.getByText("1 asset", { exact: true })).toBeVisible()
  await expect(supportPage.getByText("Filtered by your live permissions")).toHaveCount(0)
  await expect(
    supportPage.getByText("Approved capability packs and reusable assets you can use now."),
  ).toHaveCount(0)
  await expect(
    supportPage.getByRole("heading", { name: "L1 Customer Support Capability Onboarding" }),
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

async function assetHarness(page: Page, actor: "owner" | "support") {
  const requests: string[] = []
  const unexpectedRequests: string[] = []
  const browserErrors: string[] = []
  const completed = new Set<string>()

  page.on("pageerror", (error) => browserErrors.push(error.message))
  page.on("console", (message) => {
    if (message.type() === "error") browserErrors.push(message.text())
  })

  await page.route("**/api/**", async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const signature = `${request.method()} ${url.pathname}`
    requests.push(signature)

    if (url.pathname === "/api/session") {
      await json(route, session(actor))
      return
    }
    if (url.pathname === "/api/session/csrf") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        headers: { "set-cookie": "XSRF-TOKEN=golden-poc-token; Path=/" },
        body: JSON.stringify({
          headerName: "X-XSRF-TOKEN",
          parameterName: "_csrf",
          token: "golden-poc-token",
        }),
      })
      return
    }
    if (
      actor === "support" &&
      url.pathname === "/api/assets/catalog"
    ) {
      await json(route, {
        items: [
          {
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
          },
        ],
        total: 1,
        page: 1,
        pageSize: 24,
        totalPages: 1,
        sort: "RECENTLY_RELEASED",
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

    unexpectedRequests.push(signature)
    await json(route, { message: "Unexpected golden POC request" }, 500)
  })

  return { requests, unexpectedRequests, browserErrors }
}

async function skillGovernanceHarness(page: Page) {
  const requests: string[] = []
  const unexpectedRequests: string[] = []
  const browserErrors: string[] = []
  let submitted = false

  page.on("pageerror", (error) => browserErrors.push(error.message))
  page.on("console", (message) => {
    if (message.type() === "error") browserErrors.push(message.text())
  })

  await page.route("**/api/**", async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const signature = `${request.method()} ${url.pathname}`
    requests.push(signature)

    if (url.pathname === "/api/session") {
      await json(route, session("owner"))
      return
    }
    if (url.pathname === "/api/session/csrf") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        headers: { "set-cookie": "XSRF-TOKEN=skill-governance-token; Path=/" },
        body: JSON.stringify({
          headerName: "X-XSRF-TOKEN",
          parameterName: "_csrf",
          token: "skill-governance-token",
        }),
      })
      return
    }
    if (url.pathname === `/api/assets/${SKILL_ID}`) {
      await json(route, skillDraftAsset(submitted))
      return
    }
    if (url.pathname === `/api/assets/${SKILL_ID}/governance-actions`) {
      await json(route, {
        canSubmitReview: true,
        canReview: false,
        canPublish: false,
        canWithdraw: false,
      })
      return
    }
    if (
      request.method() === "POST" &&
      url.pathname === `/api/assets/${SKILL_ID}/submissions`
    ) {
      submitted = true
      await json(route, skillDraftAsset(true))
      return
    }

    unexpectedRequests.push(signature)
    await json(route, { message: "Unexpected Skill Governance request" }, 500)
  })

  return { requests, unexpectedRequests, browserErrors }
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

function skillDraftAsset(submitted: boolean) {
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
    portfolioState: "ACTIVE",
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
    revisions: submitted
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
            changeNote: "Ready for governed review",
            createdByUserId: OWNER_ID,
            createdAt: timestamp,
          },
        ]
      : [],
    reviews: submitted
      ? [
          {
            id: SKILL_REVIEW_ID,
            revisionId: SKILL_REVISION_ID,
            revisionDigest: SKILL_DIGEST,
            state: "IN_REVIEW",
            policyVersion: "asset-review-v1",
            requestedByUserId: OWNER_ID,
            createdAt: timestamp,
            decisions: [],
          },
        ]
      : [],
    releases: [],
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
