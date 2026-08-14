import type { AssistantFileView } from "@/lib/hey-api"

export type { AssistantFileView } from "@/lib/hey-api"

export const MAX_ASSISTANT_PRIVATE_FILES = 3
export const ASSISTANT_FILE_ACCEPT =
  ".txt,.md,.csv,.html,.htm,.rtf,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.odt,.ods,.odp"

export type AssistantFileStatus = AssistantFileView["status"]

export function assistantFileShouldPoll(status?: AssistantFileStatus) {
  return status === undefined || status === "UPLOADED" || status === "PROCESSING"
}

export function assistantFileStatusLabel(status?: AssistantFileStatus) {
  switch (status) {
    case "UPLOADED":
      return "Queued"
    case "PROCESSING":
      return "Processing"
    case "READY":
      return "Ready"
    case "FAILED":
      return "Failed"
    case "DELETING":
      return "Deleting"
    case "DELETED":
      return "Deleted"
    case "EXPIRED":
      return "Expired"
    default:
      return "Status unavailable"
  }
}
