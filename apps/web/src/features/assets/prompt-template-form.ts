import type { CreateAssetRequest, Draft, UpdateAssetDraftRequest } from "@/lib/hey-api"

export type PromptVariableType = "STRING" | "INTEGER" | "NUMBER" | "BOOLEAN" | "STRING_LIST"
export type PromptClassification = "PUBLIC" | "INTERNAL" | "CONFIDENTIAL" | "RESTRICTED"

export type PromptVariableForm = {
  key: string
  name: string
  type: PromptVariableType
  required: boolean
  sensitive: boolean
  defaultValue: string
  pattern: string
  allowedValues: string
}

export type PromptEvaluationCaseForm = {
  key: string
  name: string
  values: Record<string, string>
  expectedContains: string
  forbiddenContains: string
  sensitiveFixtureAcknowledged: boolean
}

export type PromptTemplateFormValue = {
  title: string
  summary: string
  namespace: string
  slug: string
  knowledgeSpaceId: string
  classification: PromptClassification
  objective: string
  audience: string
  textTemplate: string
  variables: PromptVariableForm[]
  evaluationCases: PromptEvaluationCaseForm[]
  grounding: "NONE" | "OPTIONAL"
  knowledgeRequirements: string
  useWhen: string
  doNotUseWhen: string
  knownLimitations: string
  outputContract: string
}

type PromptPayload = {
  objective?: string
  audience?: string
  useWhen?: string[]
  doNotUseWhen?: string[]
  textTemplate?: string
  messages?: Array<{ role?: string; content?: string }>
  variables?: Array<{
    name?: string
    type?: PromptVariableType
    required?: boolean
    defaultValue?: unknown
    sensitive?: boolean
    pattern?: string
    allowedValues?: string[]
  }>
  outputContract?: Record<string, unknown>
  knowledgeRequirements?: string[]
  evaluationCases?: Array<{
    name?: string
    variables?: Record<string, unknown>
    expectedContains?: string[]
    forbiddenContains?: string[]
  }>
  knownLimitations?: string
}

export type PromptDraftParseResult =
  | { kind: "text"; value: PromptTemplateFormValue }
  | { kind: "messages"; messages: Array<{ role: string; content: string }> }
  | { kind: "invalid" }

export type PromptDraftBuildResult =
  | {
      ok: true
      request: CreateAssetRequest & { draft: Required<CreateAssetRequest>["draft"] & { payload: string } }
      update: UpdateAssetDraftRequest & { payload: string }
    }
  | { ok: false; message: string }

const VARIABLE_NAME = /^[a-z][a-z0-9_]{0,63}$/
const PORTABLE_ID = /^[a-z0-9](?:[a-z0-9._-]{0,126}[a-z0-9])?$/

export function createEmptyPromptForm(): PromptTemplateFormValue {
  return {
    title: "",
    summary: "",
    namespace: "",
    slug: "",
    knowledgeSpaceId: "",
    classification: "INTERNAL",
    objective: "",
    audience: "",
    textTemplate: "",
    variables: [],
    evaluationCases: [],
    grounding: "NONE",
    knowledgeRequirements: "",
    useWhen: "",
    doNotUseWhen: "",
    knownLimitations: "",
    outputContract: "",
  }
}

export function slugifyPromptTitle(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 128)
}

export function extractPromptPlaceholders(template: string): string[] {
  const seen = new Set<string>()
  const names: string[] = []
  for (const match of template.matchAll(/{{\s*([a-z][a-z0-9_]{0,63})\s*}}/g)) {
    const name = match[1]
    if (!seen.has(name)) {
      seen.add(name)
      names.push(name)
    }
  }
  return names
}

export function parsePromptDraft(payload: string | undefined, draft?: Draft): PromptDraftParseResult {
  try {
    const parsed = JSON.parse(payload ?? "") as PromptPayload
    const messages = (parsed.messages ?? [])
      .filter((message): message is { role: string; content: string } =>
        Boolean(message.role && message.content),
      )
      .map((message) => ({ role: message.role, content: message.content }))
    if (messages.length) return { kind: "messages", messages }
    if (!parsed.textTemplate) return { kind: "invalid" }

    return {
      kind: "text",
      value: {
        ...createEmptyPromptForm(),
        title: draft?.title ?? "",
        summary: draft?.summary ?? "",
        classification: (draft?.classification as PromptClassification | undefined) ?? "INTERNAL",
        objective: parsed.objective ?? "",
        audience: parsed.audience ?? "",
        textTemplate: parsed.textTemplate,
        variables: (parsed.variables ?? []).map((variable, index) => ({
          key: variable.name ?? `variable-${index}`,
          name: variable.name ?? "",
          type: variable.type ?? "STRING",
          required: Boolean(variable.required),
          sensitive: Boolean(variable.sensitive),
          defaultValue: formatFormValue(variable.defaultValue),
          pattern: variable.pattern ?? "",
          allowedValues: (variable.allowedValues ?? []).join("\n"),
        })),
        evaluationCases: (parsed.evaluationCases ?? []).map((evaluationCase, index) => ({
          key: `${evaluationCase.name ?? "case"}-${index}`,
          name: evaluationCase.name ?? "",
          values: Object.fromEntries(
            Object.entries(evaluationCase.variables ?? {}).map(([name, value]) => [
              name,
              formatFormValue(value),
            ]),
          ),
          expectedContains: (evaluationCase.expectedContains ?? []).join("\n"),
          forbiddenContains: (evaluationCase.forbiddenContains ?? []).join("\n"),
          sensitiveFixtureAcknowledged: false,
        })),
        grounding: parsed.knowledgeRequirements?.length ? "OPTIONAL" : "NONE",
        knowledgeRequirements: (parsed.knowledgeRequirements ?? []).join("\n"),
        useWhen: (parsed.useWhen ?? []).join("\n"),
        doNotUseWhen: (parsed.doNotUseWhen ?? []).join("\n"),
        knownLimitations: parsed.knownLimitations ?? "",
        outputContract: Object.keys(parsed.outputContract ?? {}).length
          ? JSON.stringify(parsed.outputContract, null, 2)
          : "",
      },
    }
  } catch {
    return { kind: "invalid" }
  }
}

export function buildPromptAssetDraft(value: PromptTemplateFormValue): PromptDraftBuildResult {
  const title = value.title.trim()
  const summary = value.summary.trim()
  const namespace = value.namespace.trim().toLowerCase()
  const slug = value.slug.trim().toLowerCase()
  if (!title) return invalid("Enter a Prompt name.")
  if (!summary) return invalid("Enter a short summary.")
  if (!PORTABLE_ID.test(namespace)) return invalid("Enter a portable namespace.")
  if (!PORTABLE_ID.test(slug)) return invalid("Enter a portable slug.")
  if (!value.knowledgeSpaceId) return invalid("Choose a Knowledge Space.")
  if (!value.objective.trim()) return invalid("Describe the Prompt objective.")
  if (!value.audience.trim()) return invalid("Describe who should use this Prompt.")
  if (!value.textTemplate.trim()) return invalid("Write the Prompt template.")
  if (value.variables.length > 50) return invalid("A Prompt supports at most 50 variables.")
  if (value.evaluationCases.length > 10) return invalid("A Prompt supports at most 10 test cases.")

  const evaluationCaseNames = value.evaluationCases.map((evaluationCase) =>
    evaluationCase.name.trim(),
  )
  if (new Set(evaluationCaseNames).size !== evaluationCaseNames.length) {
    return invalid("Test case names must be unique.")
  }

  const variableNames = value.variables.map((variable) => variable.name.trim())
  const invalidName = variableNames.find((name) => !VARIABLE_NAME.test(name))
  if (invalidName) return invalid(`Use lower_snake_case for variable "${invalidName}".`)
  if (new Set(variableNames).size !== variableNames.length) {
    return invalid("Prompt variable names must be unique.")
  }
  const unresolved = extractPromptPlaceholders(value.textTemplate).filter(
    (name) => !variableNames.includes(name),
  )
  if (unresolved.length) {
    return invalid(`Define every prompt placeholder before saving: ${unresolved.join(", ")}.`)
  }

  let outputContract: Record<string, unknown> = {}
  if (value.outputContract.trim()) {
    try {
      const parsed = JSON.parse(value.outputContract) as unknown
      if (!parsed || Array.isArray(parsed) || typeof parsed !== "object") {
        return invalid("Output contract must be a JSON object.")
      }
      outputContract = parsed as Record<string, unknown>
    } catch {
      return invalid("Output contract must be valid JSON.")
    }
  }

  let variables: PromptPayload["variables"]
  let evaluationCases: PromptPayload["evaluationCases"]
  try {
    variables = value.variables.map((variable) => {
      const defaultValue = variable.defaultValue.trim()
      return {
        name: variable.name.trim(),
        type: variable.type,
        required: variable.required,
        ...(defaultValue ? { defaultValue: typedValue(variable, defaultValue) } : {}),
        sensitive: variable.sensitive,
        pattern: variable.pattern.trim(),
        allowedValues: lines(variable.allowedValues),
      }
    })
    evaluationCases = value.evaluationCases.map((evaluationCase) => {
      const name = evaluationCase.name.trim()
      if (!name) throw new Error("Name every test case.")
      const unknownVariable = Object.keys(evaluationCase.values).find(
        (variableName) => !variableNames.includes(variableName),
      )
      if (unknownVariable) {
        throw new Error(`Case "${name}" has an unknown variable "${unknownVariable}".`)
      }
      const expectedContains = lines(evaluationCase.expectedContains)
      const forbiddenContains = lines(evaluationCase.forbiddenContains)
      if (!expectedContains.length && !forbiddenContains.length) {
        throw new Error(`Add an expected or forbidden fragment to case "${name}".`)
      }
      const usesSensitive = value.variables.some(
        (variable) => variable.sensitive && evaluationCase.values[variable.name]?.trim(),
      )
      if (usesSensitive && !evaluationCase.sensitiveFixtureAcknowledged) {
        throw new Error(
          `Confirm that case "${name}" uses synthetic data for sensitive variables.`,
        )
      }
      const caseVariables = Object.fromEntries(
        value.variables.flatMap((variable) => {
          const raw = evaluationCase.values[variable.name]?.trim() ?? ""
          if (!raw) {
            if (variable.required && !variable.defaultValue.trim()) {
              throw new Error(`Case "${name}" needs variable "${variable.name}".`)
            }
            return []
          }
          return [[variable.name, typedValue(variable, raw)]]
        }),
      )
      return { name, variables: caseVariables, expectedContains, forbiddenContains }
    })
  } catch (failure) {
    return invalid(failure instanceof Error ? failure.message : "The Prompt payload is invalid.")
  }

  const payload = JSON.stringify({
    objective: value.objective.trim(),
    audience: value.audience.trim(),
    useWhen: lines(value.useWhen),
    doNotUseWhen: lines(value.doNotUseWhen),
    textTemplate: value.textTemplate,
    messages: [],
    variables,
    outputContract,
    dataPolicy: { retainRawVariables: false, retainRawOutput: false },
    compatibility: ["chat"],
    knowledgeRequirements:
      value.grounding === "OPTIONAL" ? lines(value.knowledgeRequirements) : [],
    evaluationCases,
    knownLimitations: value.knownLimitations.trim(),
  })
  const draft = {
    title,
    summary,
    classification: value.classification,
    schemaVersion: "1",
    payload,
  }
  return {
    ok: true,
    request: {
      type: "PROMPT_TEMPLATE",
      namespace,
      slug,
      knowledgeSpaceId: value.knowledgeSpaceId,
      draft,
    },
    update: draft,
  }
}

function lines(value: string): string[] {
  return value
    .split("\n")
    .map((item) => item.trim())
    .filter(Boolean)
}

function typedValue(variable: PromptVariableForm, raw: string): unknown {
  if (variable.type === "STRING") return raw
  if (variable.type === "STRING_LIST") {
    return raw
      .split(/,|\n/)
      .map((item) => item.trim())
      .filter(Boolean)
  }
  if (variable.type === "BOOLEAN") {
    if (raw !== "true" && raw !== "false") {
      throw new Error(`Variable "${variable.name}" must be true or false.`)
    }
    return raw === "true"
  }
  const number = Number(raw)
  if (!Number.isFinite(number) || (variable.type === "INTEGER" && !Number.isInteger(number))) {
    throw new Error(
      `Variable "${variable.name}" must be ${variable.type === "INTEGER" ? "an integer" : "a number"}.`,
    )
  }
  return number
}

function formatFormValue(value: unknown): string {
  if (value === undefined || value === null) return ""
  if (Array.isArray(value)) return value.join(", ")
  if (typeof value === "object") return JSON.stringify(value)
  return String(value)
}

function invalid(message: string): PromptDraftBuildResult {
  return { ok: false, message }
}
