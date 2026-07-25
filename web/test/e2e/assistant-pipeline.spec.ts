import { expect, test, type Page, type Route } from "@playwright/test"

const FIRST_CHUNK_ID = "43000000-0000-0000-0000-000000000003"
const SECOND_CHUNK_ID = "43000000-0000-0000-0000-000000000004"

interface AssistantHarnessOptions {
  chatFrames?: string[]
  citationResponses?: Record<
    string,
    { status: number; contentType?: string; body?: string | Buffer }
  >
  holdChat?: boolean
}

async function assistantHarness(page: Page, options: AssistantHarnessOptions = {}) {
  const requests: string[] = []
  const unexpectedRequests: string[] = []
  const browserErrors: string[] = []
  let releaseChat: (() => void) | undefined
  const chatRelease = new Promise<void>((resolve) => {
    releaseChat = resolve
  })

  page.on("pageerror", (error) => browserErrors.push(error.message))
  page.on("console", (message) => {
    if (message.type() === "error") browserErrors.push(message.text())
  })

  await page.route("**/api/**", async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (!url.pathname.startsWith("/api/")) {
      await route.continue()
      return
    }
    requests.push(`${request.method()} ${url.pathname}`)

    if (url.pathname === "/api/session") {
      await json(route, {
        authenticated: true,
        name: "Playwright User",
        email: "playwright@example.test",
        userId: "41000000-0000-0000-0000-000000000001",
        organizationId: "41000000-0000-0000-0000-000000000002",
        departmentId: "41000000-0000-0000-0000-000000000003",
        role: "EMPLOYEE",
      })
      return
    }

    if (url.pathname === "/api/session/csrf") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        headers: { "set-cookie": "XSRF-TOKEN=playwright-token; Path=/" },
        body: JSON.stringify({
          headerName: "X-XSRF-TOKEN",
          parameterName: "_csrf",
          token: "playwright-token",
        }),
      })
      return
    }

    if (url.pathname === "/api/assistant/chat") {
      if (options.holdChat) await chatRelease
      await route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        headers: { "x-vercel-ai-ui-message-stream": "v1" },
        body: sse(options.chatFrames ?? citedAnswerFrames()),
      })
      return
    }

    const citation = options.citationResponses?.[url.pathname]
    if (citation) {
      await route.fulfill({
        status: citation.status,
        contentType: citation.contentType,
        body: citation.body,
      })
      return
    }

    unexpectedRequests.push(`${request.method()} ${url.pathname}`)
    await json(route, { message: "Unexpected E2E request" }, 500)
  })

  return {
    requests,
    unexpectedRequests,
    browserErrors,
    releaseChat: () => releaseChat?.(),
  }
}

test("anchors only server-declared citations and opens the matching source", async ({ page }) => {
  const secondPath = `/api/citations/${SECOND_CHUNK_ID}/content`
  const harness = await assistantHarness(page, {
    citationResponses: {
      [secondPath]: {
        status: 200,
        contentType: "text/plain",
        body: "Expense claims require the original receipt.",
      },
    },
  })
  await page.goto("/")
  await submit(page, "How do I submit an expense claim?")

  await expect(page.getByText("Use the approved form")).toBeVisible()
  await expect(page.getByRole("button", { name: "Open source 1: Employee Handbook" })).toHaveCount(1)
  await expect(page.getByRole("button", { name: "Open source 2: Expense Policy" })).toHaveCount(2)
  await expect(page.getByRole("button", { name: /Open source 9/ })).toHaveCount(0)
  await expect(page.getByText(/verify exceptions \[9]\./)).toBeVisible()

  await page.getByRole("button", { name: "Open source 2: Expense Policy" }).first().click()
  await expect(page.getByRole("complementary", { name: "Answer sources" })).toBeVisible()
  await page.getByRole("button", { name: "Preview source 2: Expense Policy" }).click()
  await expect(page.getByText("Expense claims require the original receipt.")).toBeVisible()
  expect(harness.requests.filter((request) => request === `GET ${secondPath}`)).toHaveLength(1)
  expect(harness.unexpectedRequests).toEqual([])
  expect(harness.browserErrors).toEqual([])
})

test("shows an opaque citation error after access is revoked", async ({ page }) => {
  const firstPath = `/api/citations/${FIRST_CHUNK_ID}/content`
  const harness = await assistantHarness(page, {
    chatFrames: singleSourceFrames(),
    citationResponses: {
      [firstPath]: {
        status: 404,
        contentType: "application/problem+json",
        body: JSON.stringify({ title: "Not Found", detail: "opaque" }),
      },
    },
  })
  await page.goto("/")
  await submit(page, "What is the probation policy?")

  await page.getByRole("button", { name: "Open source 1: Employee Handbook" }).click()
  await page.getByRole("button", { name: "Preview source 1: Employee Handbook" }).click()
  await expect(
    page.getByText("The source changed or you no longer have access."),
  ).toBeVisible()
  await expect(page.getByText("opaque")).toHaveCount(0)
  expect(harness.unexpectedRequests).toEqual([])
  expect(unexpectedBrowserErrors(harness.browserErrors, [404])).toEqual([])
})

test("previews an authorized PDF through the protected citation endpoint", async ({ page }) => {
  const firstPath = `/api/citations/${FIRST_CHUNK_ID}/content`
  const harness = await assistantHarness(page, {
    chatFrames: singleSourceFrames(),
    citationResponses: {
      [firstPath]: {
        status: 200,
        contentType: "application/pdf",
        body: minimalPdf(),
      },
    },
  })
  await page.goto("/")
  await submit(page, "What is the probation policy?")

  await page.getByRole("button", { name: "Open source 1: Employee Handbook" }).click()
  await page.getByRole("button", { name: "Preview source 1: Employee Handbook" }).click()
  await expect(page.locator('iframe[title="Employee Handbook"]')).toBeVisible()
  expect(harness.requests.filter((request) => request === `GET ${firstPath}`)).toHaveLength(1)
  expect(harness.unexpectedRequests).toEqual([])
  expect(harness.browserErrors).toEqual([])
})

test("renders a safe answer without source UI when no evidence is available", async ({ page }) => {
  const harness = await assistantHarness(page, {
    chatFrames: textOnlyFrames(
      "I could not find enough accessible company knowledge to answer that question.",
    ),
  })
  await page.goto("/")
  await submit(page, "Show me the financial forecast")

  await expect(
    page.getByText("I could not find enough accessible company knowledge to answer that question."),
  ).toBeVisible()
  await expect(page.getByText(/Used \d+ sources/)).toHaveCount(0)
  await expect(page.getByRole("button", { name: /Open source/ })).toHaveCount(0)
  expect(harness.unexpectedRequests).toEqual([])
  expect(harness.browserErrors).toEqual([])
})

test("reports provider failure and retries with exactly one new request", async ({ page }) => {
  const harness = await assistantHarness(page, {
    chatFrames: [
      frame({ type: "start", messageId: "assistant-error" }),
      frame({ type: "error", errorText: "The assistant stream failed." }),
      "data: [DONE]",
    ],
  })
  await page.goto("/")
  await submit(page, "What is the probation policy?")

  await expect(page.getByRole("alert")).toContainText("OrgMemory could not complete this turn.")
  expect(harness.requests.filter((request) => request === "POST /api/assistant/chat")).toHaveLength(1)
  await page.getByRole("button", { name: "Retry" }).click()
  await expect
    .poll(
      () => harness.requests.filter((request) => request === "POST /api/assistant/chat").length,
    )
    .toBe(2)
  expect(harness.unexpectedRequests).toEqual([])
  expect(harness.browserErrors).toEqual([])
})

test("stop aborts one in-flight assistant request", async ({ page }) => {
  const harness = await assistantHarness(page, { holdChat: true })
  await page.goto("/")
  await submit(page, "What is the probation policy?", false)

  await expect(page.getByRole("button", { name: "Stop" })).toBeVisible()
  await page.getByRole("button", { name: "Stop" }).click()
  await expect(page.getByRole("button", { name: "Submit" })).toBeVisible()
  harness.releaseChat()
  expect(harness.requests.filter((request) => request === "POST /api/assistant/chat")).toHaveLength(1)
  expect(harness.unexpectedRequests).toEqual([])
  expect(harness.browserErrors).toEqual([])
})

async function submit(page: Page, message: string, awaitDispatch = true) {
  const composer = page.getByPlaceholder("Ask OrgMemory…")
  await composer.waitFor({ state: "visible" })
  await composer.fill(message)
  await composer.press("Enter")
  if (awaitDispatch) {
    await expect(page.getByText(message, { exact: true })).toBeVisible()
  }
}

function citedAnswerFrames() {
  return [
    frame({ type: "start", messageId: "assistant-cited" }),
    frame({ type: "start-step" }),
    sourceFrame(1, FIRST_CHUNK_ID, "Employee Handbook"),
    sourceFrame(2, SECOND_CHUNK_ID, "Expense Policy"),
    frame({ type: "text-start", id: "answer" }),
    frame({
      type: "text-delta",
      id: "answer",
      delta: "Use the approved form [1], attach receipts [2][2], and verify exceptions [9].",
    }),
    frame({ type: "text-end", id: "answer" }),
    frame({ type: "finish-step" }),
    frame({ type: "finish", finishReason: "stop" }),
    "data: [DONE]",
  ]
}

function singleSourceFrames() {
  return [
    frame({ type: "start", messageId: "assistant-revoked" }),
    frame({ type: "start-step" }),
    sourceFrame(1, FIRST_CHUNK_ID, "Employee Handbook"),
    frame({ type: "text-start", id: "answer" }),
    frame({ type: "text-delta", id: "answer", delta: "The probation period is 60 days [1]." }),
    frame({ type: "text-end", id: "answer" }),
    frame({ type: "finish-step" }),
    frame({ type: "finish", finishReason: "stop" }),
    "data: [DONE]",
  ]
}

function textOnlyFrames(text: string) {
  return [
    frame({ type: "start", messageId: "assistant-empty" }),
    frame({ type: "start-step" }),
    frame({ type: "text-start", id: "answer" }),
    frame({ type: "text-delta", id: "answer", delta: text }),
    frame({ type: "text-end", id: "answer" }),
    frame({ type: "finish-step" }),
    frame({ type: "finish", finishReason: "stop" }),
    "data: [DONE]",
  ]
}

function sourceFrame(number: number, chunkId: string, title: string) {
  return frame({
    type: "source-url",
    sourceId: `urn:orgmemory:citation:${number}:${chunkId}`,
    url: `/api/citations/${chunkId}/content`,
    title,
    providerMetadata: {
      orgmemory: { citationNumber: number },
    },
  })
}

function frame(value: Record<string, unknown>) {
  return `data: ${JSON.stringify(value)}`
}

function sse(frames: string[]) {
  return `${frames.join("\n\n")}\n\n`
}

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  })
}

function unexpectedBrowserErrors(errors: string[], allowedHttpStatuses: number[] = []) {
  return errors.filter(
    (error) =>
      !allowedHttpStatuses.some((status) =>
        error.includes(`server responded with a status of ${status}`),
      ),
  )
}

function minimalPdf() {
  const objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>",
  ]
  let pdf = "%PDF-1.4\n"
  const offsets = objects.map((object, index) => {
    const offset = Buffer.byteLength(pdf)
    pdf += `${index + 1} 0 obj\n${object}\nendobj\n`
    return offset
  })
  const xrefOffset = Buffer.byteLength(pdf)
  pdf += `xref\n0 ${objects.length + 1}\n`
  pdf += "0000000000 65535 f \n"
  for (const offset of offsets) {
    pdf += `${offset.toString().padStart(10, "0")} 00000 n \n`
  }
  pdf += `trailer\n<< /Root 1 0 R /Size ${objects.length + 1} >>\n`
  pdf += `startxref\n${xrefOffset}\n%%EOF\n`
  return Buffer.from(pdf, "ascii")
}
