import {
  ArrowDown,
  ArrowUp,
  Braces,
  ChevronDown,
  FlaskConical,
  LockKeyhole,
  Plus,
  ShieldCheck,
  Trash2,
} from "lucide-react"
import { type FormEvent, type ReactNode } from "react"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import {
  extractPromptPlaceholders,
  slugifyPromptTitle,
  type PromptClassification,
  type PromptEvaluationCaseForm,
  type PromptTemplateFormValue,
  type PromptVariableForm,
  type PromptVariableType,
} from "@/features/assets/prompt-template-form"
import type { KnowledgeSpaceResponse } from "@/lib/hey-api"

const VARIABLE_TYPES: Array<{ value: PromptVariableType; label: string }> = [
  { value: "STRING", label: "Text" },
  { value: "INTEGER", label: "Integer" },
  { value: "NUMBER", label: "Number" },
  { value: "BOOLEAN", label: "True / false" },
  { value: "STRING_LIST", label: "Text list" },
]

type PromptTemplateEditorProps = {
  value: PromptTemplateFormValue
  onChange: (value: PromptTemplateFormValue) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  submitLabel: string
  submitting?: boolean
  error?: string
  spaces?: KnowledgeSpaceResponse[]
  placementLocked?: boolean
  asideAction?: ReactNode
}

export function PromptTemplateEditor({
  value,
  onChange,
  onSubmit,
  submitLabel,
  submitting = false,
  error,
  spaces = [],
  placementLocked = false,
  asideAction,
}: PromptTemplateEditorProps) {
  const placeholders = extractPromptPlaceholders(value.textTemplate)
  const unresolved = placeholders.filter(
    (placeholder) => !value.variables.some((variable) => variable.name === placeholder),
  )

  function patch(next: Partial<PromptTemplateFormValue>) {
    onChange({ ...value, ...next })
  }

  function changeTitle(title: string) {
    const previousGenerated = slugifyPromptTitle(value.title)
    patch({
      title,
      slug:
        !value.slug || value.slug === previousGenerated ? slugifyPromptTitle(title) : value.slug,
    })
  }

  function addVariable(name = "") {
    if (value.variables.length >= 50) return
    patch({ variables: [...value.variables, newVariable(name)] })
  }

  function updateVariable(index: number, variable: PromptVariableForm) {
    patch({
      variables: value.variables.map((current, currentIndex) =>
        currentIndex === index ? variable : current,
      ),
    })
  }

  function removeVariable(index: number) {
    patch({ variables: value.variables.filter((_, currentIndex) => currentIndex !== index) })
  }

  function addEvaluationCase() {
    if (value.evaluationCases.length >= 10) return
    patch({ evaluationCases: [...value.evaluationCases, newEvaluationCase()] })
  }

  function updateEvaluationCase(index: number, evaluationCase: PromptEvaluationCaseForm) {
    patch({
      evaluationCases: value.evaluationCases.map((current, currentIndex) =>
        currentIndex === index ? evaluationCase : current,
      ),
    })
  }

  function moveEvaluationCase(index: number, direction: -1 | 1) {
    const target = index + direction
    if (target < 0 || target >= value.evaluationCases.length) return
    const next = [...value.evaluationCases]
    ;[next[index], next[target]] = [next[target], next[index]]
    patch({ evaluationCases: next })
  }

  return (
    <form onSubmit={onSubmit} className="space-y-6">
      <section className="grid gap-5 rounded-xl border border-border-default bg-surface-raised p-5 md:grid-cols-2">
        <Field label="Prompt name" id="prompt-name">
          <Input
            id="prompt-name"
            value={value.title}
            maxLength={256}
            autoComplete="off"
            disabled={submitting}
            placeholder="Support ticket classifier"
            onChange={(event) => changeTitle(event.currentTarget.value)}
          />
        </Field>
        <Field label="Description" id="prompt-summary" hint="What this Prompt does and when it helps.">
          <Input
            id="prompt-summary"
            value={value.summary}
            maxLength={1024}
            disabled={submitting}
            placeholder="Classifies incoming support tickets by category and urgency."
            onChange={(event) => patch({ summary: event.currentTarget.value })}
          />
        </Field>
      </section>

      <div className="grid min-w-0 gap-6 xl:grid-cols-[minmax(0,1fr)_23rem]">
        <div className="min-w-0 space-y-6">
          <Card className="gap-0 overflow-hidden bg-surface-raised py-0 shadow-none">
            <CardHeader className="flex-row items-center justify-between border-b border-border-subtle px-5 py-4">
              <div>
                <CardTitle>Prompt</CardTitle>
                <p className="mt-1 text-xs leading-5 text-content-muted">
                  Insert variables with {"{{lower_snake_case}}"}.
                </p>
              </div>
              <Badge variant="outline" className="font-mono">Text template</Badge>
            </CardHeader>
            <CardContent className="p-0">
              <Textarea
                id="prompt-template"
                aria-label="Prompt template"
                value={value.textTemplate}
                maxLength={30_000}
                rows={17}
                spellCheck={false}
                disabled={submitting}
                className="min-h-[25rem] resize-y rounded-none border-0 bg-surface-raised px-6 py-5 font-mono text-sm leading-7 shadow-none focus-visible:ring-0"
                placeholder={"Classify this support ticket.\n\nTicket:\n{{ticket_text}}\n\nReturn the category and rationale."}
                onChange={(event) => patch({ textTemplate: event.currentTarget.value })}
              />
            </CardContent>
          </Card>

          <section aria-labelledby="prompt-variables-heading" className="space-y-3">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div>
                <h2 id="prompt-variables-heading" className="text-section-title">Variables</h2>
                <p className="mt-1 text-supporting text-content-secondary">
                  Typed values are validated before any provider call.
                </p>
              </div>
              <Button type="button" variant="outline" size="sm" onClick={() => addVariable()} disabled={submitting || value.variables.length >= 50}>
                <Plus aria-hidden="true" />Add variable
              </Button>
            </div>

            {unresolved.length ? (
              <Alert>
                <Braces aria-hidden="true" />
                <AlertDescription className="flex flex-wrap items-center justify-between gap-3">
                  <span>Define detected placeholders: {unresolved.join(", ")}.</span>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() =>
                      patch({
                        variables: [
                          ...value.variables,
                          ...unresolved.slice(0, 50 - value.variables.length).map(newVariable),
                        ],
                      })
                    }
                  >
                    Add detected
                  </Button>
                </AlertDescription>
              </Alert>
            ) : null}

            {value.variables.length ? (
              <div className="divide-y divide-border-subtle overflow-hidden rounded-xl border border-border-default bg-surface-raised">
                {value.variables.map((variable, index) => (
                  <VariableRow
                    key={variable.key}
                    variable={variable}
                    disabled={submitting}
                    onChange={(next) => updateVariable(index, next)}
                    onRemove={() => removeVariable(index)}
                  />
                ))}
              </div>
            ) : (
              <div className="rounded-xl border border-dashed border-border-default px-5 py-8 text-center text-supporting text-content-muted">
                Add a variable or type a placeholder in the Prompt.
              </div>
            )}
          </section>

          <Collapsible defaultOpen className="rounded-xl border border-border-default bg-surface-raised">
            <CollapsibleTrigger className="group flex w-full items-center justify-between gap-3 px-5 py-4 text-left outline-none focus-visible:ring-2 focus-visible:ring-focus-ring">
              <div>
                <h2 className="text-section-title">Usage contract</h2>
                <p className="mt-1 text-supporting text-content-secondary">Who this Prompt is for, the task it performs, and its safe-use boundaries.</p>
              </div>
              <ChevronDown className="size-4 transition-transform group-data-[state=open]:rotate-180" aria-hidden="true" />
            </CollapsibleTrigger>
            <CollapsibleContent className="grid gap-5 border-t border-border-subtle p-5 md:grid-cols-2">
              <Field label="Task objective" id="prompt-objective">
                <Input
                  id="prompt-objective"
                  value={value.objective}
                  maxLength={2000}
                  disabled={submitting}
                  placeholder="Classify and route incoming support tickets"
                  onChange={(event) => patch({ objective: event.currentTarget.value })}
                />
              </Field>
              <Field
                label="Intended users"
                id="prompt-audience"
                hint="Descriptive metadata only. This does not grant access."
              >
                <Input
                  id="prompt-audience"
                  value={value.audience}
                  maxLength={1000}
                  disabled={submitting}
                  placeholder="L1 Support"
                  onChange={(event) => patch({ audience: event.currentTarget.value })}
                />
              </Field>
              <Field label="Use when" id="prompt-use-when" hint="One condition per line.">
                <Textarea id="prompt-use-when" value={value.useWhen} rows={4} disabled={submitting} onChange={(event) => patch({ useWhen: event.currentTarget.value })} />
              </Field>
              <Field label="Do not use when" id="prompt-do-not-use" hint="One condition per line.">
                <Textarea id="prompt-do-not-use" value={value.doNotUseWhen} rows={4} disabled={submitting} onChange={(event) => patch({ doNotUseWhen: event.currentTarget.value })} />
              </Field>
              <Field label="Known limitations" id="prompt-limitations" hint="Be explicit about cases that need human judgment or another workflow.">
                <Textarea id="prompt-limitations" value={value.knownLimitations} rows={6} disabled={submitting} onChange={(event) => patch({ knownLimitations: event.currentTarget.value })} />
              </Field>
            </CollapsibleContent>
          </Collapsible>

          <Collapsible className="rounded-xl border border-border-default bg-surface-raised">
            <CollapsibleTrigger className="group flex w-full items-center justify-between gap-3 px-5 py-4 text-left outline-none focus-visible:ring-2 focus-visible:ring-focus-ring">
              <div>
                <h2 className="text-section-title">Output contract</h2>
                <p className="mt-1 text-supporting text-content-secondary">Optional JSON shape expected from this Prompt.</p>
              </div>
              <ChevronDown className="size-4 transition-transform group-data-[state=open]:rotate-180" aria-hidden="true" />
            </CollapsibleTrigger>
            <CollapsibleContent className="border-t border-border-subtle p-5">
              <Field label="JSON object" id="prompt-output-contract" hint="Leave empty when the response is free-form text.">
                <Textarea id="prompt-output-contract" value={value.outputContract} rows={8} spellCheck={false} className="font-mono text-xs" disabled={submitting} placeholder={'{\n  "type": "object"\n}'} onChange={(event) => patch({ outputContract: event.currentTarget.value })} />
              </Field>
            </CollapsibleContent>
          </Collapsible>
        </div>

        <aside className="space-y-5 xl:sticky xl:top-6 xl:self-start">
          <TestCasesPanel
            cases={value.evaluationCases}
            variables={value.variables}
            disabled={submitting}
            onAdd={addEvaluationCase}
            onChange={updateEvaluationCase}
            onMove={moveEvaluationCase}
            onRemove={(index) => patch({ evaluationCases: value.evaluationCases.filter((_, currentIndex) => currentIndex !== index) })}
          />

          <Card className="gap-0 bg-surface-raised py-0 shadow-none">
            <CardHeader className="border-b border-border-subtle px-5 py-4">
              <div className="flex items-center gap-2">
                <ShieldCheck className="size-5 text-action-primary" aria-hidden="true" />
                <CardTitle>Knowledge grounding</CardTitle>
              </div>
            </CardHeader>
            <CardContent className="space-y-4 p-5">
              <div className="grid grid-cols-2 rounded-lg border border-control-border p-1" aria-label="Knowledge grounding mode">
                {(["NONE", "OPTIONAL"] as const).map((mode) => (
                  <Button
                    key={mode}
                    type="button"
                    size="sm"
                    variant="ghost"
                    aria-pressed={value.grounding === mode}
                    className={value.grounding === mode ? "bg-surface-selected text-content-primary" : "text-content-secondary"}
                    onClick={() => patch({ grounding: mode })}
                  >
                    {mode === "NONE" ? "None" : "Optional"}
                  </Button>
                ))}
              </div>
              {value.grounding === "OPTIONAL" ? (
                <Field label="Knowledge requirements" id="prompt-knowledge" hint="One natural-language query hint per line. This stores no Space or source id.">
                  <Textarea id="prompt-knowledge" value={value.knowledgeRequirements} rows={5} disabled={submitting} placeholder="support runbook\nSLA escalation policy" onChange={(event) => patch({ knowledgeRequirements: event.currentTarget.value })} />
                </Field>
              ) : null}
              <p className="text-xs leading-5 text-content-muted">
                Optional grounding uses permission-verified evidence when available. The Prompt can still run without evidence.
              </p>
            </CardContent>
          </Card>

          <PlacementPanel value={value} spaces={spaces} locked={placementLocked} disabled={submitting} onChange={patch} />

          {error ? <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert> : null}

          <Button type="submit" size="lg" className="w-full" disabled={submitting}>
            {submitLabel}
          </Button>
          {asideAction}
        </aside>
      </div>
    </form>
  )
}

function VariableRow({ variable, disabled, onChange, onRemove }: { variable: PromptVariableForm; disabled: boolean; onChange: (value: PromptVariableForm) => void; onRemove: () => void }) {
  return (
    <div className="grid gap-4 p-4 lg:grid-cols-[minmax(10rem,1fr)_10rem_auto] lg:items-start">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
        <Field label="Name" id={`prompt-variable-${variable.key}`}>
          <Input id={`prompt-variable-${variable.key}`} value={variable.name} className="font-mono" disabled={disabled} placeholder="ticket_text" onChange={(event) => onChange({ ...variable, name: event.currentTarget.value })} />
        </Field>
        <Field label="Default value" id={`prompt-default-${variable.key}`}>
          <Input id={`prompt-default-${variable.key}`} value={variable.defaultValue} disabled={disabled} placeholder="Optional" onChange={(event) => onChange({ ...variable, defaultValue: event.currentTarget.value })} />
        </Field>
      </div>
      <div className="space-y-4">
        <Field label="Type" id={`prompt-type-${variable.key}`}>
          <Select value={variable.type} disabled={disabled} onValueChange={(type) => onChange({ ...variable, type: type as PromptVariableType })}>
            <SelectTrigger id={`prompt-type-${variable.key}`} className="w-full"><SelectValue /></SelectTrigger>
            <SelectContent>{VARIABLE_TYPES.map((type) => <SelectItem key={type.value} value={type.value}>{type.label}</SelectItem>)}</SelectContent>
          </Select>
        </Field>
        <div className="flex flex-wrap gap-4 pt-1">
          <CheckField id={`prompt-required-${variable.key}`} label="Required" checked={variable.required} disabled={disabled} onCheckedChange={(checked) => onChange({ ...variable, required: checked })} />
          <CheckField id={`prompt-sensitive-${variable.key}`} label="Sensitive" checked={variable.sensitive} disabled={disabled} onCheckedChange={(checked) => onChange({ ...variable, sensitive: checked })} />
        </div>
      </div>
      <Button type="button" variant="ghost" size="icon" aria-label={`Remove variable ${variable.name || "unnamed"}`} disabled={disabled} onClick={onRemove}>
        <Trash2 aria-hidden="true" />
      </Button>
      <div className="grid gap-4 sm:grid-cols-2 lg:col-span-3">
        <Field label="Allowed values" id={`prompt-allowed-${variable.key}`} hint="Optional; one value per line.">
          <Textarea id={`prompt-allowed-${variable.key}`} rows={2} value={variable.allowedValues} disabled={disabled} onChange={(event) => onChange({ ...variable, allowedValues: event.currentTarget.value })} />
        </Field>
        <Field label="Pattern" id={`prompt-pattern-${variable.key}`} hint="Optional regular expression for text.">
          <Input id={`prompt-pattern-${variable.key}`} value={variable.pattern} disabled={disabled || variable.type !== "STRING"} className="font-mono" onChange={(event) => onChange({ ...variable, pattern: event.currentTarget.value })} />
        </Field>
      </div>
    </div>
  )
}

function TestCasesPanel({ cases, variables, disabled, onAdd, onChange, onMove, onRemove }: { cases: PromptEvaluationCaseForm[]; variables: PromptVariableForm[]; disabled: boolean; onAdd: () => void; onChange: (index: number, value: PromptEvaluationCaseForm) => void; onMove: (index: number, direction: -1 | 1) => void; onRemove: (index: number) => void }) {
  return (
    <Card className="gap-0 bg-surface-raised py-0 shadow-none">
      <CardHeader className="flex-row items-center justify-between border-b border-border-subtle px-5 py-4">
        <div className="flex items-center gap-2"><FlaskConical className="size-5 text-content-muted" aria-hidden="true" /><CardTitle>Test cases</CardTitle></div>
        <Badge variant="outline">{cases.length}/10</Badge>
      </CardHeader>
      <CardContent className="space-y-3 p-4">
        <p className="text-xs leading-5 text-content-muted">Fixtures are saved in the immutable release. Use synthetic data only.</p>
        {cases.map((evaluationCase, index) => (
          <Collapsible key={evaluationCase.key} className="rounded-lg border border-border-default">
            <div className="flex items-center gap-2 px-3 py-2">
              <CollapsibleTrigger className="group flex min-w-0 flex-1 items-center justify-between gap-2 text-left text-supporting font-medium outline-none focus-visible:ring-2 focus-visible:ring-focus-ring">
                <span className="truncate">{index + 1}. {evaluationCase.name || "Untitled case"}</span>
                <ChevronDown className="size-4 shrink-0 transition-transform group-data-[state=open]:rotate-180" aria-hidden="true" />
              </CollapsibleTrigger>
              <Button type="button" size="icon" variant="ghost" aria-label={`Move test case ${index + 1} up`} disabled={disabled || index === 0} onClick={() => onMove(index, -1)}><ArrowUp aria-hidden="true" /></Button>
              <Button type="button" size="icon" variant="ghost" aria-label={`Move test case ${index + 1} down`} disabled={disabled || index === cases.length - 1} onClick={() => onMove(index, 1)}><ArrowDown aria-hidden="true" /></Button>
              <Button type="button" size="icon" variant="ghost" aria-label={`Remove test case ${index + 1}`} disabled={disabled} onClick={() => onRemove(index)}><Trash2 aria-hidden="true" /></Button>
            </div>
            <CollapsibleContent className="space-y-4 border-t border-border-subtle p-3">
              <Field label="Case name" id={`prompt-case-${evaluationCase.key}`}>
                <Input id={`prompt-case-${evaluationCase.key}`} value={evaluationCase.name} disabled={disabled} onChange={(event) => onChange(index, { ...evaluationCase, name: event.currentTarget.value })} />
              </Field>
              {variables.map((variable) => (
                <Field key={variable.key} label={variable.name || "Unnamed variable"} id={`prompt-case-${evaluationCase.key}-${variable.key}`} hint={variable.sensitive ? "Sensitive variable — use a synthetic fixture." : undefined}>
                  <Input id={`prompt-case-${evaluationCase.key}-${variable.key}`} value={evaluationCase.values[variable.name] ?? ""} disabled={disabled || !variable.name} onChange={(event) => onChange(index, { ...evaluationCase, values: { ...evaluationCase.values, [variable.name]: event.currentTarget.value }, sensitiveFixtureAcknowledged: variable.sensitive ? false : evaluationCase.sensitiveFixtureAcknowledged })} />
                </Field>
              ))}
              <Field label="Expected fragments" id={`prompt-case-expected-${evaluationCase.key}`} hint="One per line.">
                <Textarea id={`prompt-case-expected-${evaluationCase.key}`} value={evaluationCase.expectedContains} rows={3} disabled={disabled} onChange={(event) => onChange(index, { ...evaluationCase, expectedContains: event.currentTarget.value })} />
              </Field>
              <Field label="Forbidden fragments" id={`prompt-case-forbidden-${evaluationCase.key}`} hint="One per line.">
                <Textarea id={`prompt-case-forbidden-${evaluationCase.key}`} value={evaluationCase.forbiddenContains} rows={3} disabled={disabled} onChange={(event) => onChange(index, { ...evaluationCase, forbiddenContains: event.currentTarget.value })} />
              </Field>
              {variables.some((variable) => variable.sensitive && evaluationCase.values[variable.name]?.trim()) ? (
                <CheckField id={`prompt-synthetic-${evaluationCase.key}`} label="I confirm these are synthetic, non-secret values" checked={evaluationCase.sensitiveFixtureAcknowledged} disabled={disabled} onCheckedChange={(checked) => onChange(index, { ...evaluationCase, sensitiveFixtureAcknowledged: checked })} />
              ) : null}
            </CollapsibleContent>
          </Collapsible>
        ))}
        <Button type="button" variant="outline" className="w-full" disabled={disabled || cases.length >= 10} onClick={onAdd}><Plus aria-hidden="true" />Add test case</Button>
      </CardContent>
    </Card>
  )
}

function PlacementPanel({ value, spaces, locked, disabled, onChange }: { value: PromptTemplateFormValue; spaces: KnowledgeSpaceResponse[]; locked: boolean; disabled: boolean; onChange: (value: Partial<PromptTemplateFormValue>) => void }) {
  const validSpaces = spaces.filter((space): space is KnowledgeSpaceResponse & { id: string; name: string } => Boolean(space.id && space.name))
  return (
    <Collapsible defaultOpen={!locked} className="rounded-xl border border-border-default bg-surface-raised">
      <CollapsibleTrigger className="group flex w-full items-center justify-between gap-3 px-5 py-4 text-left outline-none focus-visible:ring-2 focus-visible:ring-focus-ring">
        <div className="flex items-center gap-2"><LockKeyhole className="size-4 text-content-muted" aria-hidden="true" /><span className="text-label">Governance placement</span></div>
        <ChevronDown className="size-4 transition-transform group-data-[state=open]:rotate-180" aria-hidden="true" />
      </CollapsibleTrigger>
      <CollapsibleContent className="space-y-4 border-t border-border-subtle p-5">
        <Field label="Namespace" id="prompt-namespace">
          <Input id="prompt-namespace" value={value.namespace} className="font-mono" disabled={disabled || locked} placeholder="support" onChange={(event) => onChange({ namespace: event.currentTarget.value })} />
        </Field>
        <Field label="Slug" id="prompt-slug">
          <Input id="prompt-slug" value={value.slug} className="font-mono" disabled={disabled || locked} placeholder="support-ticket-classifier" onChange={(event) => onChange({ slug: event.currentTarget.value })} />
        </Field>
        <Field label="Knowledge Space" id="prompt-space" hint="Governance placement only; this is not a grounding target.">
          {locked ? (
            <Input id="prompt-space" value={validSpaces.find((space) => space.id === value.knowledgeSpaceId)?.name ?? "Current governed Space"} disabled />
          ) : (
            <Select value={value.knowledgeSpaceId} disabled={disabled || validSpaces.length === 0} onValueChange={(knowledgeSpaceId) => onChange({ knowledgeSpaceId })}>
              <SelectTrigger id="prompt-space" className="w-full"><SelectValue placeholder={validSpaces.length ? "Choose a space" : "No creation target"} /></SelectTrigger>
              <SelectContent>{validSpaces.map((space) => <SelectItem key={space.id} value={space.id}>{space.name}</SelectItem>)}</SelectContent>
            </Select>
          )}
        </Field>
        <Field label="Classification" id="prompt-classification">
          <Select value={value.classification} disabled={disabled} onValueChange={(classification) => onChange({ classification: classification as PromptClassification })}>
            <SelectTrigger id="prompt-classification" className="w-full"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="PUBLIC">Public</SelectItem>
              <SelectItem value="INTERNAL">Internal</SelectItem>
              <SelectItem value="CONFIDENTIAL">Confidential</SelectItem>
              <SelectItem value="RESTRICTED">Restricted</SelectItem>
            </SelectContent>
          </Select>
        </Field>
      </CollapsibleContent>
    </Collapsible>
  )
}

function CheckField({ id, label, checked, disabled, onCheckedChange }: { id: string; label: string; checked: boolean; disabled: boolean; onCheckedChange: (checked: boolean) => void }) {
  return <div className="flex items-center gap-2"><Checkbox id={id} checked={checked} disabled={disabled} onCheckedChange={(next) => onCheckedChange(next === true)} /><Label htmlFor={id} className="text-xs font-normal text-content-secondary">{label}</Label></div>
}

function Field({ label, id, hint, children }: { label: string; id: string; hint?: string; children: ReactNode }) {
  return <div className="space-y-2"><Label htmlFor={id}>{label}</Label>{children}{hint ? <p className="text-xs leading-5 text-content-muted">{hint}</p> : null}</div>
}

function newVariable(name: string): PromptVariableForm {
  return { key: key(), name, type: "STRING", required: true, sensitive: false, defaultValue: "", pattern: "", allowedValues: "" }
}

function newEvaluationCase(): PromptEvaluationCaseForm {
  return { key: key(), name: "", values: {}, expectedContains: "", forbiddenContains: "", sensitiveFixtureAcknowledged: false }
}

function key(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
}
