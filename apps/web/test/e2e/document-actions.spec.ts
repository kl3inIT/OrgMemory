import { expect, test, type Page } from "@playwright/test"

const ORGANIZATION_ID = "11111111-1111-4111-8111-111111111111"
const USER_ID = "22222222-2222-4222-8222-222222222222"
const READY_SOURCE_ID = "33333333-3333-4333-8333-333333333333"
const PROCESSING_SOURCE_ID = "44444444-4444-4444-8444-444444444444"
const ASSET_ID = "55555555-5555-4555-8555-555555555555"

test("views protected evidence and deletes only an eligible ready upload", async ({ page }) => {
  const requests: string[] = []
  const browserErrors: string[] = []
  let readyDeleted = false
  page.on("pageerror", (error) => browserErrors.push(error.message))
  page.on("console", (message) => {
    if (message.type() === "error") browserErrors.push(message.text())
  })

  await documentHarness(page, requests, () => readyDeleted, () => {
    readyDeleted = true
  })
  await page.goto("/sources")

  await page.getByRole("button", { name: "Employee handbook", exact: true }).click()
  await expect(page.getByRole("heading", { name: "Employee handbook" })).toBeVisible()
  await expect(page.getByText("Employees receive 12 days of annual leave.")).toBeVisible()
  await expect(page.getByRole("link", { name: "Download" })).toBeVisible()
  await page.getByRole("button", { name: "Close" }).click()

  await page.getByRole("button", { name: "Actions for Employee handbook" }).click()
  await page.getByRole("menuitem", { name: "Delete" }).click()
  await expect(page.getByRole("heading", { name: "Delete this document?" })).toBeVisible()
  await page.getByRole("button", { name: "Delete document" }).click()

  await expect(page.getByText("Document deleted from active knowledge.")).toBeVisible()
  await expect(page.getByRole("button", { name: "Employee handbook", exact: true })).toHaveCount(0)
  await expect(page.getByRole("button", { name: "Actions for Quarterly plan" })).toBeVisible()
  await page.getByRole("button", { name: "Actions for Quarterly plan" }).click()
  await expect(page.getByRole("menuitem", { name: "Delete unavailable" })).toBeDisabled()
  expect(requests).toContain(`GET /api/sources/${READY_SOURCE_ID}/content`)
  expect(requests).toContain(`DELETE /api/sources/${READY_SOURCE_ID}`)
  expect(browserErrors).toEqual([])
})

async function documentHarness(
  page: Page,
  requests: string[],
  isDeleted: () => boolean,
  markDeleted: () => void,
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
          name: "Employee",
          email: "employee@example.test",
          userId: USER_ID,
          organizationId: ORGANIZATION_ID,
          departmentId: null,
          role: "USER",
        }),
      })
    }
    if (url.pathname === "/api/session/csrf") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        headers: { "set-cookie": "XSRF-TOKEN=document-actions-csrf; Path=/" },
        body: JSON.stringify({
          headerName: "X-XSRF-TOKEN",
          parameterName: "_csrf",
          token: "document-actions-csrf",
        }),
      })
    }
    if (url.pathname === "/api/knowledge-spaces/upload-targets") {
      return route.fulfill({ status: 200, contentType: "application/json", body: "[]" })
    }
    if (url.pathname === "/api/sources" && request.method() === "GET") {
      const sources = [
        ...(!isDeleted()
          ? [
              source({
                id: READY_SOURCE_ID,
                title: "Employee handbook",
                status: "READY",
                knowledgeAssetId: ASSET_ID,
                contentAvailable: true,
                deletionAllowed: true,
              }),
            ]
          : []),
        source({
          id: PROCESSING_SOURCE_ID,
          title: "Quarterly plan",
          status: "PARSING",
          knowledgeAssetId: null,
          contentAvailable: false,
          deletionAllowed: false,
        }),
      ]
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(sources),
      })
    }
    if (url.pathname === `/api/sources/${READY_SOURCE_ID}/content`) {
      return route.fulfill({
        status: 200,
        contentType: "text/plain",
        headers: {
          "cache-control": "no-store",
          "content-disposition": "inline; filename=employee-handbook.txt",
          "x-content-type-options": "nosniff",
        },
        body: "Employees receive 12 days of annual leave.",
      })
    }
    if (url.pathname === `/api/sources/${READY_SOURCE_ID}` && request.method() === "DELETE") {
      markDeleted()
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          knowledgeAssetId: ASSET_ID,
          knowledgeAssetVersionId: "66666666-6666-4666-8666-666666666666",
          normalizedRecordId: "77777777-7777-4777-8777-777777777777",
          rawSourceObjectId: "88888888-8888-4888-8888-888888888888",
          sourceAclSnapshotId: "99999999-9999-4999-8999-999999999999",
          status: "RETIRED",
        }),
      })
    }
    return route.fulfill({ status: 404, contentType: "application/json", body: "{}" })
  })
}

function source(input: {
  id: string
  title: string
  status: string
  knowledgeAssetId: string | null
  contentAvailable: boolean
  deletionAllowed: boolean
}) {
  return {
    ...input,
    sourceSystem: "upload",
    aclAuthority: "ORGMEMORY",
    classification: "INTERNAL",
    fileName: `${input.title.toLowerCase().replaceAll(" ", "-")}.txt`,
    mediaType: "text/plain",
    contentLength: 48,
    failureCode: null,
    failureMessage: null,
    embeddingProfileKey: input.status === "READY" ? "openai:text-embedding-3-large:1536" : null,
    embeddingProvider: input.status === "READY" ? "openai" : null,
    embeddingModel: input.status === "READY" ? "text-embedding-3-large" : null,
    embeddingDimensions: input.status === "READY" ? 1536 : null,
    createdAt: "2026-08-02T01:00:00Z",
    updatedAt: "2026-08-02T02:00:00Z",
  }
}
