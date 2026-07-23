/**
 * The order configuring a connection actually has to happen in.
 *
 * <p>The credential comes first because it is what reports the connection key — a Slack
 * workspace id, a Google Workspace domain — and there is nothing to configure until that is
 * known. After it come the two halves the source has no opinion about: where crawled content
 * lands, and how much to read.
 *
 * <p>This sits apart from the wizard because the route validates the step out of the address
 * before the wizard is rendered, and a module that exports both a component and a constant
 * cannot be hot-reloaded.
 */
export const WIZARD_STEPS = [
  { key: "credential", label: "Credential", hint: "Which account" },
  { key: "destination", label: "Destination", hint: "Where it lands" },
  { key: "scope", label: "Scope", hint: "How much it reads" },
] as const

export type WizardStep = (typeof WIZARD_STEPS)[number]["key"]

/** Whether an address names a step this wizard has. An unknown one is not one. */
export function isWizardStep(value: unknown): value is WizardStep {
  return WIZARD_STEPS.some((step) => step.key === value)
}
