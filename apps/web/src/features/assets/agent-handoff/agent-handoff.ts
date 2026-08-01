/**
 * Display-only instructions for handing a bounded task to a local agent.
 *
 * This contract never executes a command and never represents authorization.
 * The CLI and server re-authorize every state-changing operation.
 */
export type AgentHandoff = {
  title: string
  promptTemplate?: string
  cliCommand: string
  prerequisites: readonly string[]
  requiredScopes: readonly string[]
  confirmationBoundary: string
  completionNote: string
}
