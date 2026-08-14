import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest"

import { PromptDraftWorkspace } from "@/features/assets/components/prompt-draft-workspace"
import {
  buildPromptAssetDraft,
  createEmptyPromptForm,
} from "@/features/assets/prompt-template-form"
import type { AssetGovernanceActions, AssetView } from "@/lib/hey-api"

const mocks = vi.hoisted(() => ({
  publish: vi.fn(),
  toastSuccess: vi.fn(),
  update: vi.fn(),
}))

vi.mock("sonner", () => ({ toast: { success: mocks.toastSuccess } }))
vi.mock("@/lib/hey-api/@tanstack/react-query.gen", () => ({
  listKnowledgeSpaceUploadTargetsOptions: () => ({
    queryKey: ["knowledge-space-upload-targets"],
    queryFn: async () => [
      {
        id: "88888888-8888-4888-8888-888888888802",
        key: "support",
        name: "Support knowledge",
      },
    ],
  }),
  publishAssetDraftMutation: () => ({ mutationFn: mocks.publish }),
  updateAssetDraftMutation: () => ({ mutationFn: mocks.update }),
}))

describe("PromptDraftWorkspace", () => {
  beforeAll(() => {
    vi.stubGlobal(
      "ResizeObserver",
      class {
        observe() {}
        unobserve() {}
        disconnect() {}
      },
    )
  })

  beforeEach(() => {
    mocks.publish.mockReset()
    mocks.toastSuccess.mockReset()
    mocks.update.mockReset().mockResolvedValue({})
  })

  afterAll(() => vi.unstubAllGlobals())

  it("preserves local content when optimistic locking rejects a save", async () => {
    const user = userEvent.setup()
    const onChanged = vi.fn<() => Promise<unknown>>()
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    mocks.update.mockRejectedValue({
      detail: "The working copy changed. Refresh before saving again.",
    })

    render(
      <QueryClientProvider client={queryClient}>
        <PromptDraftWorkspace
          asset={promptAsset()}
          actions={{ canEdit: true } as AssetGovernanceActions}
          canPublish={false}
          onChanged={onChanged}
          onPublished={vi.fn()}
        />
      </QueryClientProvider>,
    )

    const objective = screen.getByLabelText("Task objective")
    fireEvent.change(objective, { target: { value: "Route urgent synthetic tickets" } })
    await user.click(screen.getByRole("button", { name: "Save working copy" }))

    expect(
      await screen.findByText("The working copy changed. Refresh before saving again."),
    ).toBeVisible()
    expect(objective).toHaveValue("Route urgent synthetic tickets")
    expect(onChanged).not.toHaveBeenCalled()
  })

  it("disables mutation controls when edit capability is revoked", async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })

    render(
      <QueryClientProvider client={queryClient}>
        <PromptDraftWorkspace
          asset={promptAsset()}
          actions={{ canEdit: false } as AssetGovernanceActions}
          canPublish={false}
          onChanged={vi.fn()}
          onPublished={vi.fn()}
        />
      </QueryClientProvider>,
    )

    expect(
      await screen.findByText("You can view this working copy but cannot edit it."),
    ).toBeVisible()
    expect(screen.getByLabelText("Prompt name")).toBeDisabled()
    expect(screen.getByRole("button", { name: "Save working copy" })).toBeDisabled()
  })

  it("preserves a successful save when refresh fails and retries only the refresh", async () => {
    const user = userEvent.setup()
    const onChanged = vi
      .fn<() => Promise<unknown>>()
      .mockRejectedValueOnce(new Error("refresh unavailable"))
      .mockResolvedValue(undefined)
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })

    render(
      <QueryClientProvider client={queryClient}>
        <PromptDraftWorkspace
          asset={promptAsset()}
          actions={{ canEdit: true } as AssetGovernanceActions}
          canPublish={false}
          onChanged={onChanged}
          onPublished={vi.fn()}
        />
      </QueryClientProvider>,
    )

    await user.click(screen.getByRole("button", { name: "Save working copy" }))

    expect(mocks.update).toHaveBeenCalledOnce()
    expect(mocks.toastSuccess).toHaveBeenCalledWith("Prompt working copy saved")
    expect(
      await screen.findByText(
        "The working copy was saved, but the latest revision could not be loaded.",
      ),
    ).toBeVisible()
    expect(screen.getByRole("button", { name: "Save working copy" })).toBeDisabled()

    await user.click(screen.getByRole("button", { name: "Retry refresh" }))

    await waitFor(() => {
      expect(
        screen.queryByText(
          "The working copy was saved, but the latest revision could not be loaded.",
        ),
      ).not.toBeInTheDocument()
    })
    expect(onChanged).toHaveBeenCalledTimes(2)
    expect(mocks.update).toHaveBeenCalledOnce()
  })

  it("preserves a successful publication until release history refresh recovers", async () => {
    const user = userEvent.setup()
    const onChanged = vi
      .fn<() => Promise<unknown>>()
      .mockRejectedValueOnce(new Error("refresh unavailable"))
      .mockResolvedValue(undefined)
    const onPublished = vi.fn()
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    mocks.publish.mockResolvedValue({})

    render(
      <QueryClientProvider client={queryClient}>
        <PromptDraftWorkspace
          asset={promptAsset()}
          actions={{ canEdit: true } as AssetGovernanceActions}
          canPublish
          onChanged={onChanged}
          onPublished={onPublished}
        />
      </QueryClientProvider>,
    )

    await user.type(screen.getByLabelText("Version"), "1.0.0")
    await user.click(screen.getByRole("button", { name: "Publish release" }))
    await user.click(
      await screen.findByRole("button", { name: "Confirm publish release" }),
    )

    expect(mocks.publish).toHaveBeenCalledOnce()
    expect(mocks.toastSuccess).toHaveBeenCalledWith("Immutable Release published")
    expect(
      await screen.findByText(
        "The release was published, but the latest release history could not be loaded.",
      ),
    ).toBeVisible()
    expect(onPublished).not.toHaveBeenCalled()

    await user.click(screen.getByRole("button", { name: "Retry refresh" }))

    await waitFor(() => expect(onPublished).toHaveBeenCalledOnce())
    expect(onChanged).toHaveBeenCalledTimes(2)
    expect(mocks.publish).toHaveBeenCalledOnce()
  })
})

function promptAsset(): AssetView {
  const built = buildPromptAssetDraft({
    ...createEmptyPromptForm(),
    title: "Support ticket classifier",
    summary: "Classifies incoming support tickets.",
    namespace: "support",
    slug: "support-ticket-classifier",
    knowledgeSpaceId: "88888888-8888-4888-8888-888888888802",
    objective: "Classify incoming support tickets",
    audience: "L1 Support",
    textTemplate: "Classify this support ticket.",
  })
  if (!built.ok) throw new Error(built.message)

  return {
    id: "a3000000-0000-0000-0000-000000000001",
    type: "PROMPT_TEMPLATE",
    namespace: "support",
    slug: "support-ticket-classifier",
    knowledgeSpaceId: "88888888-8888-4888-8888-888888888802",
    portfolioState: "DRAFT_ONLY",
    authorizationReady: true,
    ownerUserId: "44444444-4444-4444-4444-444444444444",
    sharingState: "PRIVATE",
    draft: {
      id: "a3000000-0000-0000-0000-000000000004",
      lockVersion: 0,
      ...built.update,
      editedByUserId: "44444444-4444-4444-4444-444444444444",
      updatedAt: "2026-08-11T08:00:00Z",
    },
    revisions: [],
    reviews: [],
    releases: [],
    ownershipHealth: {
      ownerPresent: true,
      backupOwnerPresent: false,
      orphaned: false,
      continuityAtRisk: false,
    },
    roleAssignments: [],
  }
}
