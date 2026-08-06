import { expect, test, type Page } from "@playwright/test"

const ORGANIZATION_ID = "11111111-1111-4111-8111-111111111111"
const USER_ID = "22222222-2222-4222-8222-222222222222"
const READY_SOURCE_ID = "33333333-3333-4333-8333-333333333333"
const PROCESSING_SOURCE_ID = "44444444-4444-4444-8444-444444444444"
const ASSET_ID = "55555555-5555-4555-8555-555555555555"
const MARKDOWN_SOURCE_ID = "66666666-6666-4666-8666-666666666666"
const PDF_SOURCE_ID = "77777777-7777-4777-8777-777777777777"
const IMAGE_SOURCE_ID = "88888888-8888-4888-8888-888888888888"
const OFFICE_SOURCE_ID = "99999999-9999-4999-8999-999999999999"
const RETRY_SOURCE_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
const FAILED_SOURCE_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
const QUARANTINED_SOURCE_ID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
const OUT_OF_SCOPE_SOURCE_ID = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
const PEOPLE_SPACE_ID = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
const FINANCE_SPACE_ID = "ffffffff-ffff-4fff-8fff-ffffffffffff"

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

test("Knowledge presents safe cross-format evidence in a centered document viewer", async ({
  page,
}, testInfo) => {
  const requests: string[] = []
  const browserErrors: string[] = []
  let readyDeleted = false
  let retryAttempts = 0
  page.on("pageerror", (error) => browserErrors.push(error.message))
  page.on("console", (message) => {
    if (message.type() === "error") browserErrors.push(message.text())
  })

  await documentHarness(
    page,
    requests,
    () => readyDeleted,
    () => {
      readyDeleted = true
    },
    () => ++retryAttempts,
  )
  await page.setViewportSize({ width: 1459, height: 816 })
  await page.emulateMedia({ colorScheme: "dark" })
  await page.goto("/sources")

  await expect(page.getByRole("link", { name: "Knowledge" })).toHaveAttribute(
    "aria-current",
    "page",
  )
  await expect(page.getByRole("tab", { name: "Documents", exact: true })).toBeVisible()
  await expect(page.getByRole("tab", { name: "Knowledge graph", exact: true })).toBeVisible()
  await expect(page.getByRole("columnheader", { name: "Status" })).toBeVisible()
  await expect(page.getByRole("columnheader", { name: "Index profile" })).toHaveCount(0)
  await expect(page.getByRole("tab", { name: "All documents, 27 documents" })).toBeVisible()
  await expect(page.getByRole("tab", { name: "Processing, 8 documents" })).toBeVisible()
  await expect(page.getByRole("tab", { name: "Ready, 15 documents" })).toBeVisible()
  await expect(page.getByRole("tab", { name: "Needs attention, 4 documents" })).toBeVisible()
  await page.getByRole("combobox", { name: "Filter by Knowledge Space" }).click()
  await page.getByRole("option", { name: "People" }).click()
  await expect
    .poll(() => requests.some((request) => request.includes(`knowledgeSpaceId=${PEOPLE_SPACE_ID}`)))
    .toBe(true)
  await expect(page.getByText(/Word document/)).toBeVisible()
  await expect(page.getByText("Space: People").first()).toBeVisible()
  await expect(page.getByText("Owned by People Operations").first()).toBeVisible()
  await expect(page.getByText("Executive only")).toHaveCount(0)
  await expect(page.getByText("The document parser could not read this file.")).toBeVisible()
  await expect(page.getByText("The uploaded evidence did not pass the content policy.")).toBeVisible()

  await openDocument(page, "Quarterly plan")
  await expect(
    page.getByText("Original content becomes available after governed publication completes."),
  ).toBeVisible()
  await expect(page.getByTitle("People", { exact: true })).toBeVisible()
  await expect(page.getByText("Nguyen Van An", { exact: true })).toBeVisible()
  await closeReader(page)

  await openDocument(page, "Executive forecast")
  await expect(
    page.getByText(
      "This document's content is outside your access scope. Contact Finance to request access.",
    ),
  ).toBeVisible()
  await closeReader(page)

  await openDocument(page, "Support SLA")
  await expect(page.getByTestId("restricted-source-markdown")).toBeVisible()
  await expect(page.getByRole("heading", { name: "L1 Support SLA" })).toBeVisible()
  const markdownDownload = page.getByRole("link", { name: "Download" })
  await expect(markdownDownload).toBeVisible()
  await expect
    .poll(async () => {
      const box = await markdownDownload.boundingBox()
      return box ? Math.ceil(box.x + box.width) : Number.POSITIVE_INFINITY
    })
    .toBeLessThanOrEqual(1459)
  await expect(page.getByRole("img", { name: "Team map" })).toContainText(
    "Remote image blocked",
  )
  if (process.env.DESIGN_QA_CAPTURE) {
    const screenshot = testInfo.outputPath("knowledge-markdown-reader.png")
    await page.screenshot({ path: screenshot, fullPage: false })
    await testInfo.attach("knowledge-markdown-reader", {
      path: screenshot,
      contentType: "image/png",
    })
  }
  await page.getByRole("button", { name: "Raw" }).click()
  await expect(page.getByText("# L1 Support SLA", { exact: false })).toBeVisible()
  await closeReader(page)

  await openDocument(page, "Finance policy")
  await expect(page.getByTitle("Finance policy")).toBeVisible()
  await closeReader(page)

  await openDocument(page, "Office map")
  await expect(page.getByRole("img", { name: "Office map" })).toBeVisible()
  await closeReader(page)

  await openDocument(page, "Handover brief")
  await expect(page.getByText("Preview is unavailable for this file type")).toBeVisible()
  await expect(page.getByRole("link", { name: "Download" })).toBeVisible()
  await closeReader(page)

  await openDocument(page, "Recovery notes")
  await expect(page.getByText("The document is no longer available or permission has changed.")).toBeVisible()
  await page.getByRole("button", { name: "Retry" }).click()
  await expect(page.getByText("Recovered after a permission recheck.")).toBeVisible()

  await closeReader(page)
  await openDocument(page, "Rejected evidence")
  await page.getByRole("button", { name: "Upload corrected document" }).click()
  await expect(page.getByRole("heading", { name: "Upload a document" })).toBeVisible()
  await expect(page.getByText("Classification sets the handling baseline.", { exact: false })).toBeVisible()
  await page.getByRole("button", { name: "Cancel" }).click()

  await page.setViewportSize({ width: 390, height: 844 })
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBe(true)
  expect(retryAttempts).toBe(2)
  expect(browserErrors).toEqual([
    "Failed to load resource: the server responded with a status of 404 (Not Found)",
  ])
})

async function documentHarness(
  page: Page,
  requests: string[],
  isDeleted: () => boolean,
  markDeleted: () => void,
  nextRetryAttempt: () => number = () => 1,
) {
  await page.route("**/api/**", async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    requests.push(`${request.method()} ${url.pathname}${url.search}`)

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
          clearance: "STANDARD",
          canManageMembers: false,
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
    if (url.pathname === "/api/me") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          userId: USER_ID,
          organizationId: ORGANIZATION_ID,
          name: "Employee",
          email: "employee@example.test",
          departmentId: null,
          departmentName: null,
          clearance: "STANDARD",
        }),
      })
    }
    if (url.pathname === "/api/knowledge-spaces/upload-targets") {
      return route.fulfill({ status: 200, contentType: "application/json", body: "[]" })
    }
    if (url.pathname === "/api/knowledge-spaces/visible") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          { id: PEOPLE_SPACE_ID, key: "people", name: "People" },
          { id: FINANCE_SPACE_ID, key: "finance", name: "Finance" },
        ]),
      })
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
        source({
          id: OUT_OF_SCOPE_SOURCE_ID,
          title: "Executive forecast",
          status: "READY",
          knowledgeAssetId: ASSET_ID,
          contentAvailable: false,
          deletionAllowed: false,
          classification: "RESTRICTED",
          owningDepartmentName: "Finance",
        }),
        source({
          id: MARKDOWN_SOURCE_ID,
          title: "Support SLA",
          status: "READY",
          knowledgeAssetId: ASSET_ID,
          contentAvailable: true,
          deletionAllowed: true,
          classification: "RESTRICTED",
          fileName: "support-sla.md",
          mediaType: "text/markdown",
          contentLength: 164,
        }),
        source({
          id: PDF_SOURCE_ID,
          title: "Finance policy",
          status: "READY",
          knowledgeAssetId: ASSET_ID,
          contentAvailable: true,
          deletionAllowed: true,
          fileName: "finance-policy.pdf",
          mediaType: "application/pdf",
          contentLength: 64,
        }),
        source({
          id: IMAGE_SOURCE_ID,
          title: "Office map",
          status: "READY",
          knowledgeAssetId: ASSET_ID,
          contentAvailable: true,
          deletionAllowed: true,
          fileName: "office-map.png",
          mediaType: "image/png",
          contentLength: 68,
        }),
        source({
          id: OFFICE_SOURCE_ID,
          title: "Handover brief",
          status: "READY",
          knowledgeAssetId: ASSET_ID,
          contentAvailable: true,
          deletionAllowed: true,
          fileName: "handover-brief.docx",
          mediaType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          contentLength: 32,
        }),
        source({
          id: RETRY_SOURCE_ID,
          title: "Recovery notes",
          status: "READY",
          knowledgeAssetId: ASSET_ID,
          contentAvailable: true,
          deletionAllowed: true,
          fileName: "recovery-notes.txt",
          mediaType: "text/plain",
          contentLength: 39,
        }),
        source({
          id: FAILED_SOURCE_ID,
          title: "Unreadable handbook",
          status: "FAILED",
          knowledgeAssetId: null,
          contentAvailable: false,
          deletionAllowed: false,
          failureCode: "PARSER_FAILED",
          failureMessage: "The document parser could not read this file.",
        }),
        source({
          id: QUARANTINED_SOURCE_ID,
          title: "Rejected evidence",
          status: "QUARANTINED",
          knowledgeAssetId: null,
          contentAvailable: false,
          deletionAllowed: false,
          failureCode: "CONTENT_POLICY_REJECTED",
          failureMessage: "The uploaded evidence did not pass the content policy.",
        }),
      ]
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          items: sources,
          nextCursor: null,
          pageSize: 25,
          total: 27,
          statusCounts: { processing: 8, ready: 15, attention: 4 },
        }),
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
    if (url.pathname === `/api/sources/${MARKDOWN_SOURCE_ID}/content`) {
      return route.fulfill({
        status: 200,
        contentType: "text/plain",
        body: [
          "# L1 Support SLA",
          "",
          "**Acknowledge within 15 minutes.**",
          "",
          "![Team map](https://example.test/private-map.png)",
          "",
          "<script>window.__unsafe = true</script>",
        ].join("\n"),
      })
    }
    if (url.pathname === `/api/sources/${PDF_SOURCE_ID}/content`) {
      return route.fulfill({
        status: 200,
        contentType: "application/pdf",
        body: "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n%%EOF",
      })
    }
    if (url.pathname === `/api/sources/${IMAGE_SOURCE_ID}/content`) {
      return route.fulfill({
        status: 200,
        contentType: "image/png",
        body: Buffer.from(
          "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
          "base64",
        ),
      })
    }
    if (url.pathname === `/api/sources/${OFFICE_SOURCE_ID}/content`) {
      return route.fulfill({
        status: 200,
        contentType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        body: "PK governed office fixture",
      })
    }
    if (url.pathname === `/api/sources/${RETRY_SOURCE_ID}/content`) {
      if (nextRetryAttempt() === 1) {
        return route.fulfill({ status: 404, contentType: "application/json", body: "{}" })
      }
      return route.fulfill({
        status: 200,
        contentType: "text/plain",
        body: "Recovered after a permission recheck.",
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
  classification?: string
  fileName?: string
  mediaType?: string
  contentLength?: number
  failureCode?: string
  failureMessage?: string
  owningDepartmentName?: string
}) {
  return {
    ...input,
    sourceSystem: "upload",
    aclAuthority: "ORGMEMORY",
    classification: input.classification ?? "INTERNAL",
    fileName: input.fileName ?? `${input.title.toLowerCase().replaceAll(" ", "-")}.txt`,
    mediaType: input.mediaType ?? "text/plain",
    contentLength: input.contentLength ?? 48,
    failureCode: input.failureCode ?? null,
    failureMessage: input.failureMessage ?? null,
    knowledgeSpaceKey: "people",
    knowledgeSpaceName: "People",
    owningDepartmentName: input.owningDepartmentName ?? "People Operations",
    uploadedByName: "Nguyen Van An",
    publicationComplete: input.status === "READY" && input.knowledgeAssetId !== null,
    embeddingProfileKey: input.status === "READY" ? "openai:text-embedding-3-large:1536" : null,
    embeddingProvider: input.status === "READY" ? "openai" : null,
    embeddingModel: input.status === "READY" ? "text-embedding-3-large" : null,
    embeddingDimensions: input.status === "READY" ? 1536 : null,
    createdAt: "2026-08-02T01:00:00Z",
    updatedAt: "2026-08-02T02:00:00Z",
  }
}

async function openDocument(page: Page, title: string) {
  await page.getByRole("button", { name: title, exact: true }).click()
  await expect(page.getByRole("heading", { name: title, exact: true })).toBeVisible()
}

async function closeReader(page: Page) {
  await page.getByRole("button", { name: "Close" }).click()
  await expect(page.getByRole("dialog")).toBeHidden()
}
