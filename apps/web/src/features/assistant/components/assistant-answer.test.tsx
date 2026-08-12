import { render, screen, waitFor } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import { AssistantAnswer } from "@/features/assistant/components/assistant-answer"
import type { AssistantSourceRef } from "@/features/assistant/components/assistant-sources-panel"

const DISCLAIMER = "Câu trả lời chỉ dựa trên tài liệu bạn có quyền truy cập."

function source(citationNumber: number): AssistantSourceRef {
  return {
    id: `source-${citationNumber}`,
    citationNumber,
    title: `Sổ tay nhân sự ${citationNumber}`,
    url: `https://example.test/source-${citationNumber}`,
    available: true,
  }
}

describe("AssistantAnswer", () => {
  it("makes a marker interactive when its source arrives after the text", async () => {
    const onOpenSource = vi.fn()
    const { rerender } = render(
      <AssistantAnswer
        content="Thời gian thử việc là 60 ngày. [1]"
        sources={[]}
        showEvidenceDisclaimer
        onOpenSource={onOpenSource}
      />,
    )

    // Sources stream in after the text that cites them, so an undeclared
    // marker starts as literal text.
    expect(screen.queryByRole("button", { name: /Open source 1/ })).not.toBeInTheDocument()

    rerender(
      <AssistantAnswer
        content="Thời gian thử việc là 60 ngày. [1]"
        sources={[source(1)]}
        showEvidenceDisclaimer
        onOpenSource={onOpenSource}
      />,
    )

    const trigger = await screen.findByRole("button", { name: /Open source 1/ })
    trigger.click()
    expect(onOpenSource).toHaveBeenCalledWith("source-1")
  })

  it("keeps earlier markers interactive when a later source arrives", async () => {
    const onOpenSource = vi.fn()
    const { rerender } = render(
      <AssistantAnswer
        content="Thử việc 60 ngày. [1] Nghỉ phép 12 ngày. [2]"
        sources={[source(1)]}
        showEvidenceDisclaimer
        onOpenSource={onOpenSource}
      />,
    )

    rerender(
      <AssistantAnswer
        content="Thử việc 60 ngày. [1] Nghỉ phép 12 ngày. [2]"
        sources={[source(1), source(2)]}
        showEvidenceDisclaimer
        onOpenSource={onOpenSource}
      />,
    )

    ;(await screen.findByRole("button", { name: /Open source 1/ })).click()
    ;(await screen.findByRole("button", { name: /Open source 2/ })).click()
    expect(onOpenSource).toHaveBeenNthCalledWith(1, "source-1")
    expect(onOpenSource).toHaveBeenNthCalledWith(2, "source-2")
  })

  it("stops presenting a marker whose source is withdrawn", async () => {
    const onOpenSource = vi.fn()
    const { rerender } = render(
      <AssistantAnswer
        content="Thử việc 60 ngày. [1]"
        sources={[source(1)]}
        showEvidenceDisclaimer
        onOpenSource={onOpenSource}
      />,
    )
    expect(screen.getByRole("button", { name: /Open source 1/ })).toBeVisible()

    rerender(
      <AssistantAnswer
        content="Thử việc 60 ngày. [1]"
        sources={[]}
        showEvidenceDisclaimer
        onOpenSource={onOpenSource}
      />,
    )

    await waitFor(() =>
      expect(screen.queryByRole("button", { name: /Open source 1/ })).not.toBeInTheDocument(),
    )
  })

  it("keeps an expired private-file marker visible but inert", () => {
    const unavailable = {
      ...source(1),
      title: "Private file no longer available",
      url: "",
      available: false,
    }

    render(
      <AssistantAnswer
        content="Tài liệu nói rằng chính sách đã thay đổi. [1]"
        sources={[unavailable]}
        showEvidenceDisclaimer
        onOpenSource={vi.fn()}
      />,
    )

    expect(screen.getByLabelText("Source 1 is no longer available")).toBeVisible()
    expect(screen.queryByRole("button", { name: /Open source 1/ })).not.toBeInTheDocument()
  })

  it("keeps the rendered answer mounted while sources stream in", async () => {
    const onOpenSource = vi.fn()
    const { rerender } = render(
      <AssistantAnswer
        content="Thử việc 60 ngày. [1] Nghỉ phép 12 ngày. [2]"
        sources={[]}
        showEvidenceDisclaimer
        onOpenSource={onOpenSource}
      />,
    )
    const paragraph = screen.getByText(/Thử việc 60 ngày/)

    for (const streamed of [[source(1)], [source(1), source(2)]]) {
      rerender(
        <AssistantAnswer
          content="Thử việc 60 ngày. [1] Nghỉ phép 12 ngày. [2]"
          sources={streamed}
          showEvidenceDisclaimer
          onOpenSource={onOpenSource}
        />,
      )
    }

    // Keying the markdown on the citation contract used to unmount and reparse
    // the whole answer on every arriving source, which is quadratic over a long
    // streamed answer. The same node surviving proves it is updated in place.
    await screen.findByRole("button", { name: /Open source 2/ })
    expect(screen.getByText(/Thử việc 60 ngày/)).toBe(paragraph)
  })

  it("shows the evidence-scope disclosure below an Assistant answer", () => {
    render(
      <AssistantAnswer
        content="Thời gian thử việc là 60 ngày."
        sources={[]}
        showEvidenceDisclaimer
        onOpenSource={vi.fn()}
      />,
    )

    expect(screen.getByText("Thời gian thử việc là 60 ngày.")).toBeVisible()
    expect(screen.getByText(DISCLAIMER)).toBeVisible()
  })

  it("does not show the disclosure for a user message", () => {
    render(
      <AssistantAnswer
        content="Chính sách thử việc là gì?"
        sources={[]}
        showEvidenceDisclaimer={false}
        onOpenSource={vi.fn()}
      />,
    )

    expect(screen.queryByText(DISCLAIMER)).not.toBeInTheDocument()
  })
})
