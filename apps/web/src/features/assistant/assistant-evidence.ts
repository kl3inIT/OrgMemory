import type { AssistantEvidenceBindingView } from "@/lib/hey-api"

export const MAX_ASSISTANT_EVIDENCE_FILES = 3

export function assistantEvidenceReady(bindings: AssistantEvidenceBindingView[]) {
  return bindings.every((binding) => binding.status === "READY")
}

export function assistantEvidenceUploadDisabledReason({
  busy,
  uploading,
  targetsLoading,
  targetsError,
  targetCount,
  selectedCount,
}: {
  busy: boolean
  uploading: boolean
  targetsLoading: boolean
  targetsError: boolean
  targetCount: number
  selectedCount: number
}) {
  if (busy) return "Wait for the current turn to finish"
  if (uploading) return "A governed file is uploading"
  if (targetsLoading) return "Loading available Knowledge Spaces"
  if (targetsError) return "Knowledge Spaces could not be loaded"
  if (targetCount === 0) return "No Knowledge Space is available for governed upload"
  if (selectedCount >= MAX_ASSISTANT_EVIDENCE_FILES) {
    return "A turn can include at most three files"
  }
  return undefined
}
