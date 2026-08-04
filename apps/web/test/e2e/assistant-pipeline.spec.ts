import { expect, test, type Page, type Route } from "@playwright/test"

const FIRST_CHUNK_ID = "43000000-0000-0000-0000-000000000003"
const SECOND_CHUNK_ID = "43000000-0000-0000-0000-000000000004"
const THIRD_CHUNK_ID = "43000000-0000-0000-0000-000000000005"
const CONVERSATION_ID = "44000000-0000-4000-8000-000000000001"
const ANSWER_MESSAGE_ID = "44000000-0000-4000-8000-000000000002"
const MODEL_ACTIVATION_ID = "45000000-0000-4000-8000-000000000001"

interface HistoryMessage {
  id: string
  role: "USER" | "ASSISTANT"
  content: string
  sequence: number
  occurredAt: string
  feedback?: "HELPFUL" | "NOT_HELPFUL"
}

interface AssistantHarnessOptions {
  chatFrames?: string[]
  citationResponses?: Record<
    string,
    { status: number; contentType?: string; body?: string | Buffer }
  >
  holdChat?: boolean
  holdHistoryAfterActorSwitch?: boolean
  history?: HistoryMessage[]
  switchedActorHistory?: HistoryMessage[]
  selectedModelActivationId?: string
}

async function assistantHarness(page: Page, options: AssistantHarnessOptions = {}) {
  const requests: string[] = []
  const unexpectedRequests: string[] = []
  const browserErrors: string[] = []
  const chatBodies: Array<Record<string, unknown>> = []
  const feedbackBodies: Array<Record<string, unknown>> = []
  const modelSelectionBodies: Array<Record<string, unknown>> = []
  let releaseChat: (() => void) | undefined
  let releaseHistory: (() => void) | undefined
  let actorIndex = 1
  const chatRelease = new Promise<void>((resolve) => {
    releaseChat = resolve
  })
  const historyRelease = new Promise<void>((resolve) => {
    releaseHistory = resolve
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
        name: `Playwright User ${actorIndex}`,
        email: `playwright-${actorIndex}@example.test`,
        userId: `41000000-0000-0000-0000-${actorIndex.toString().padStart(12, "0")}`,
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

    if (
      request.method() === "GET" &&
      url.pathname === "/api/assistant/conversations"
    ) {
      await json(route, [])
      return
    }

    if (
      request.method() === "GET" &&
      url.pathname === "/api/assistant/starters"
    ) {
      await json(route, [
        {
          id: "people-policy",
          label: "People policy",
          prompt: "What is the probation policy?",
        },
        {
          id: "travel-expense",
          label: "Travel expenses",
          prompt: "How do I submit a travel expense claim?",
        },
      ])
      return
    }

    if (
      request.method() === "GET" &&
      url.pathname === "/api/assistant/model-options"
    ) {
      await json(route, {
        selectedModelActivationId: options.selectedModelActivationId,
        options: [
          {
            gatewayLabel: "Organization AI",
            provider: "custom",
            modelId: "company-default",
            displayName: "Organization default",
            defaultChoice: true,
          },
          {
            id: MODEL_ACTIVATION_ID,
            gatewayLabel: "Organization AI",
            provider: "custom",
            modelId: "claude-sonnet-4-5",
            displayName: "Claude Sonnet",
            defaultChoice: false,
          },
        ],
      })
      return
    }

    if (
      request.method() === "PUT" &&
      url.pathname === `/api/assistant/conversations/${CONVERSATION_ID}/model`
    ) {
      modelSelectionBodies.push(request.postDataJSON() as Record<string, unknown>)
      await route.fulfill({ status: 204 })
      return
    }

    if (
      request.method() === "GET" &&
      url.pathname === `/api/assistant/conversations/${CONVERSATION_ID}/messages`
    ) {
      if (actorIndex > 1 && options.holdHistoryAfterActorSwitch) {
        await historyRelease
      }
      await json(
        route,
        actorIndex > 1
          ? (options.switchedActorHistory ?? [])
          : (options.history ?? []),
      )
      return
    }

    if (url.pathname === `/api/assistant/messages/${ANSWER_MESSAGE_ID}/feedback`) {
      if (request.method() === "PUT") {
        const body = request.postDataJSON() as Record<string, unknown>
        feedbackBodies.push(body)
        await json(route, {
          messageId: ANSWER_MESSAGE_ID,
          sentiment: body.sentiment,
          updatedAt: "2026-08-04T10:00:00Z",
        })
        return
      }
      if (request.method() === "DELETE") {
        await route.fulfill({ status: 204 })
        return
      }
    }

    if (url.pathname === "/api/assistant/chat") {
      chatBodies.push(request.postDataJSON() as Record<string, unknown>)
      if (options.holdChat) await chatRelease
      const responseFrames = (options.chatFrames ?? citedAnswerFrames()).map(
        (value, index) =>
          index === 0
            ? value.replace(
                /"messageId":"[^"]+"/,
                `"messageId":"${answerMessageId(chatBodies.length)}"`,
              )
            : value,
      )
      await route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        headers: {
          "x-conversation-id": CONVERSATION_ID,
          "x-vercel-ai-ui-message-stream": "v1",
        },
        body: sse(responseFrames),
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
    chatBodies,
    feedbackBodies,
    modelSelectionBodies,
    releaseChat: () => releaseChat?.(),
    releaseHistory: () => releaseHistory?.(),
    switchActor: async () => {
      actorIndex += 1
      await page.evaluate(async () => {
        const [{ queryClient }, { router }, { getBrowserSessionQueryKey }] =
          await Promise.all([
            import("/src/lib/query-client.ts"),
            import("/src/router.tsx"),
            import("/src/lib/hey-api/@tanstack/react-query.gen.ts"),
          ])
        queryClient.removeQueries({ queryKey: getBrowserSessionQueryKey() })
        await router.invalidate()
      })
    },
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
  await expect(page.getByText("Used 2 sources")).toBeVisible()
  await expect(page.getByText("Used 3 sources")).toHaveCount(0)

  await page.getByRole("button", { name: "Open source 2: Expense Policy" }).first().click()
  await expect(page.getByRole("complementary", { name: "Answer sources" })).toBeVisible()
  await expect(page.getByRole("region", { name: "Cited sources" })).toBeVisible()
  await expect(page.getByRole("region", { name: "More" })).toBeVisible()
  await expect(page.getByRole("button", { name: "Preview source 3: Security Policy" })).toBeVisible()
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

test("loads server-owned starters and restores a session-scoped draft with focus", async ({ page }) => {
  const harness = await assistantHarness(page)
  await page.goto("/")

  const composer = page.getByPlaceholder("Ask OrgMemory…")
  await expect
    .poll(() =>
      harness.requests.includes("GET /api/assistant/starters"),
    )
    .toBe(true)
  await expect(page.getByRole("button", { name: "What is the probation policy?" })).toBeVisible()
  await expect(composer).toBeFocused()
  await composer.fill("Unsent policy question")
  await page.reload()

  await expect(page.getByPlaceholder("Ask OrgMemory…")).toHaveValue(
    "Unsent policy question",
  )
  await expect(page.getByPlaceholder("Ask OrgMemory…")).toBeFocused()
  expect(harness.unexpectedRequests).toEqual([])
})

test("chooses a governed model in the composer and sends only its opaque activation", async ({ page }) => {
  const harness = await assistantHarness(page, {
    chatFrames: textOnlyFrames("The selected model answered."),
  })
  await page.goto("/")

  await expect(page.getByText("What can we help you move forward?")).toBeVisible()
  await expect(page.getByText("Permission-aware")).toHaveCount(0)
  await page.getByRole("button", { name: /Choose model, current model Organization default/ }).click()
  await expect(page.getByPlaceholder("Search models…")).toBeVisible()
  await page.getByRole("option", { name: /Claude Sonnet/ }).click()
  await expect(page.getByRole("button", { name: /current model Claude Sonnet/ })).toBeVisible()

  await submit(page, "Which model is answering?")
  await expect.poll(() => harness.chatBodies.length).toBe(1)
  expect(harness.chatBodies[0]).toMatchObject({
    message: "Which model is answering?",
    modelActivationId: MODEL_ACTIVATION_ID,
  })
  expect(harness.chatBodies[0]).not.toHaveProperty("modelId")
  expect(harness.unexpectedRequests).toEqual([])
  expect(harness.browserErrors).toEqual([])
})

test("persists a governed model change for an existing owned conversation", async ({ page }) => {
  const harness = await assistantHarness(page, {
    history: [historyMessage(1, "USER", "Existing conversation")],
  })
  await page.goto(`/?chat=${CONVERSATION_ID}`)
  await expect(page.getByText("Existing conversation")).toBeVisible()

  await page.getByRole("button", { name: /Choose model, current model Organization default/ }).click()
  await page.getByRole("option", { name: /Claude Sonnet/ }).click()

  await expect.poll(() => harness.modelSelectionBodies.length).toBe(1)
  expect(harness.modelSelectionBodies).toEqual([
    { modelActivationId: MODEL_ACTIVATION_ID },
  ])
  expect(harness.unexpectedRequests).toEqual([])
  expect(harness.browserErrors).toEqual([])
})

test("retries a completed answer as one fresh turn and preserves the composer draft", async ({ page }) => {
  const harness = await assistantHarness(page, {
    chatFrames: textOnlyFrames("The probation period is 60 days."),
  })
  await page.goto("/")
  await submit(page, "What is the probation policy?")
  await expect(page.getByText("The probation period is 60 days.")).toBeVisible()

  const composer = page.getByPlaceholder("Ask OrgMemory…")
  await composer.fill("Keep this separate draft")
  await page.getByRole("button", { name: "Retry answer with fresh evidence" }).click()

  await expect.poll(() => harness.chatBodies.length).toBe(2)
  expect(harness.chatBodies[1]).toMatchObject({
    conversationId: CONVERSATION_ID,
    message: "What is the probation policy?",
  })
  await expect(composer).toHaveValue("Keep this separate draft")
  expect(harness.unexpectedRequests).toEqual([])
})

test("creates, replaces, removes, and replays answer feedback", async ({ page }) => {
  const harness = await assistantHarness(page, {
    history: [
      historyMessage(1, "USER", "What is the probation policy?"),
      {
        ...historyMessage(2, "ASSISTANT", "The probation period is 60 days."),
        id: ANSWER_MESSAGE_ID,
        feedback: "HELPFUL",
      },
    ],
  })
  await page.goto(`/?chat=${CONVERSATION_ID}`)

  const helpful = page.getByRole("button", { name: "Mark answer helpful" })
  const notHelpful = page.getByRole("button", { name: "Mark answer not helpful" })
  await expect(helpful).toHaveAttribute("aria-pressed", "true")
  await notHelpful.click()
  await expect(notHelpful).toHaveAttribute("aria-pressed", "true")
  expect(harness.feedbackBodies).toEqual([{ sentiment: "NOT_HELPFUL" }])

  await notHelpful.click()
  await expect(notHelpful).toHaveAttribute("aria-pressed", "false")
  expect(
    harness.requests.filter(
      (request) => request === `DELETE /api/assistant/messages/${ANSWER_MESSAGE_ID}/feedback`,
    ),
  ).toHaveLength(1)
  expect(harness.unexpectedRequests).toEqual([])
})

test("clears conversation state before rendering a different actor's history", async ({ page }) => {
  const harness = await assistantHarness(page, {
    history: [
      historyMessage(1, "USER", "Actor one private question"),
      historyMessage(2, "ASSISTANT", "Actor one private answer"),
    ],
    holdHistoryAfterActorSwitch: true,
  })
  await page.goto(`/?chat=${CONVERSATION_ID}`)
  await expect(page.getByText("Actor one private answer")).toBeVisible()

  await harness.switchActor()

  await expect(page.getByText("Actor one private answer")).toHaveCount(0)
  await expect(page.getByRole("status")).toContainText("Loading conversation")
  harness.releaseHistory()
  expect(harness.unexpectedRequests).toEqual([])
  expect(harness.browserErrors).toEqual([])
})

test("reveals scroll recovery after the reader leaves the bottom", async ({ page }) => {
  await page.setViewportSize({ width: 1100, height: 620 })
  const history = Array.from({ length: 30 }, (_, index) =>
    historyMessage(
      index + 1,
      index % 2 === 0 ? "USER" : "ASSISTANT",
      `Message ${index + 1}: ${"long governed answer ".repeat(8)}`,
    ),
  )
  const harness = await assistantHarness(page, { history })
  await page.goto(`/?chat=${CONVERSATION_ID}`)
  await expect(page.getByText("Message 30:", { exact: false })).toBeVisible()

  const conversation = page.getByRole("log")
  await conversation.evaluate((element) => {
    const scroller = element.firstElementChild as HTMLElement
    scroller.scrollTop = 0
    scroller.dispatchEvent(new Event("scroll"))
  })
  const recovery = page.getByRole("button", { name: "Scroll to bottom" })
  await expect(recovery).toBeVisible()
  await recovery.click()
  await expect(recovery).toBeHidden()
  expect(harness.unexpectedRequests).toEqual([])
})

async function submit(page: Page, message: string, awaitDispatch = true) {
  const composer = page.getByPlaceholder("Ask OrgMemory…")
  await composer.waitFor({ state: "visible" })
  await composer.fill(message)
  await composer.press("Enter")
  if (awaitDispatch) {
    await expect(
      page.locator("#main-content").getByText(message, { exact: true }),
    ).toBeVisible()
  }
}

function citedAnswerFrames() {
  return [
    frame({ type: "start", messageId: ANSWER_MESSAGE_ID }),
    frame({ type: "start-step" }),
    sourceFrame(1, FIRST_CHUNK_ID, "Employee Handbook"),
    sourceFrame(2, SECOND_CHUNK_ID, "Expense Policy"),
    sourceFrame(3, THIRD_CHUNK_ID, "Security Policy"),
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
    frame({ type: "start", messageId: ANSWER_MESSAGE_ID }),
    frame({ type: "start-step" }),
    frame({ type: "text-start", id: "answer" }),
    frame({ type: "text-delta", id: "answer", delta: text }),
    frame({ type: "text-end", id: "answer" }),
    frame({ type: "finish-step" }),
    frame({ type: "finish", finishReason: "stop" }),
    "data: [DONE]",
  ]
}

function historyMessage(
  sequence: number,
  role: "USER" | "ASSISTANT",
  content: string,
): HistoryMessage {
  return {
    id: `44000000-0000-4000-8000-${sequence.toString().padStart(12, "0")}`,
    role,
    content,
    sequence,
    occurredAt: `2026-08-04T10:${sequence.toString().padStart(2, "0")}:00Z`,
  }
}

function answerMessageId(turn: number) {
  return `44000000-0000-4000-8000-${(turn + 1).toString().padStart(12, "0")}`
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
