import { render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import { AssistantAnswer } from "@/features/assistant/components/assistant-answer"

const DISCLAIMER = "Câu trả lời chỉ dựa trên tài liệu bạn có quyền truy cập."

describe("AssistantAnswer", () => {
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
