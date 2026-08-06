import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import { GovernedDocumentViewer } from "@/features/sources/components/governed-document-viewer"
import type { SourceResponse } from "@/lib/hey-api"

describe("GovernedDocumentViewer", () => {
  it("explains that unpublished content is still completing governed publication", () => {
    renderViewer(source({ publicationComplete: false, contentAvailable: false }))

    expect(
      screen.getByText(
        "Original content becomes available after governed publication completes.",
      ),
    ).toBeVisible()
    expect(screen.queryByText(/outside your access scope/)).not.toBeInTheDocument()
    expect(screen.getByText("People")).toBeVisible()
    expect(screen.getByText("People Operations")).toBeVisible()
    expect(screen.getByText("Nguyen Van An")).toBeVisible()
  })

  it("explains an authorized ledger row whose published content is outside scope", () => {
    renderViewer(source({ publicationComplete: true, contentAvailable: false }))

    expect(
      screen.getByText(
        "This document's content is outside your access scope. Contact People Operations to request access.",
      ),
    ).toBeVisible()
    expect(
      screen.queryByText(/available after governed publication completes/),
    ).not.toBeInTheDocument()
  })
})

function renderViewer(source: SourceResponse) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <GovernedDocumentViewer
        target={{ kind: "source", source }}
        onOpenChange={vi.fn()}
      />
    </QueryClientProvider>,
  )
}

function source(
  availability: { publicationComplete: boolean; contentAvailable: boolean },
): SourceResponse {
  return {
    id: "11111111-1111-4111-8111-111111111111",
    title: "Employee handbook",
    sourceSystem: "upload",
    aclAuthority: "ORGMEMORY",
    status: availability.publicationComplete ? "READY" : "PUBLISHING",
    classification: "CONFIDENTIAL",
    fileName: "employee-handbook.pdf",
    mediaType: "application/pdf",
    contentLength: 1024,
    knowledgeAssetId: availability.publicationComplete
      ? "22222222-2222-4222-8222-222222222222"
      : undefined,
    knowledgeSpaceKey: "people",
    knowledgeSpaceName: "People",
    owningDepartmentName: "People Operations",
    uploadedByName: "Nguyen Van An",
    publicationComplete: availability.publicationComplete,
    contentAvailable: availability.contentAvailable,
    deletionAllowed: false,
    createdAt: "2026-08-06T01:00:00Z",
    updatedAt: "2026-08-06T02:00:00Z",
  }
}
