import { ChevronDown } from "lucide-react"
import { useState } from "react"

import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import type {
  ConnectorField,
  ConnectorFieldDraft,
  ConnectorFormDescriptor,
} from "@/features/admin/connector-forms"

/**
 * Renders a source's own settings from its descriptor.
 *
 * <p>One renderer for every source. A new connector contributes a descriptor and gets a form;
 * nothing here learns its name.
 */
export function ConnectorFields({
  descriptor,
  draft,
  invalid,
  onChange,
  scopes,
  children,
}: {
  descriptor: ConnectorFormDescriptor
  draft: ConnectorFieldDraft
  /** Field names that cannot be saved as typed. */
  invalid: string[]
  onChange: (name: string, value: string | boolean) => void
  /**
   * What the source says it holds, for a `scopes` field. Absent until a credential is stored,
   * because the list is read with it.
   */
  scopes?: ConnectorScopeOption[]
  /**
   * Settings every source shares, rendered alongside the source's own. They are separate
   * because they are columns with constraints rather than part of the opaque document.
   */
  children?: React.ReactNode
}) {
  const [advancedOpen, setAdvancedOpen] = useState(false)

  return (
    <div className="space-y-4">
      {descriptor.fields.map((field) => (
        <Field
          key={field.name}
          field={field}
          value={draft[field.name]}
          invalid={invalid.includes(field.name)}
          onChange={onChange}
          scopes={scopes}
        />
      ))}

      {children}

      {descriptor.advanced.length > 0 ? (
        <Collapsible open={advancedOpen} onOpenChange={setAdvancedOpen}>
          <CollapsibleTrigger className="flex items-center gap-1.5 rounded-md text-sm font-medium text-muted-foreground outline-none transition-colors hover:text-foreground focus-visible:ring-2 focus-visible:ring-focus-ring">
            <ChevronDown
              className="size-4 transition-transform data-[open=true]:rotate-180"
              data-open={advancedOpen}
              aria-hidden="true"
            />
            Advanced
          </CollapsibleTrigger>
          <CollapsibleContent className="space-y-4 pt-4">
            {descriptor.advanced.map((field) => (
              <Field
                key={field.name}
                field={field}
                value={draft[field.name]}
                invalid={invalid.includes(field.name)}
                onChange={onChange}
              />
            ))}
          </CollapsibleContent>
        </Collapsible>
      ) : null}
    </div>
  )
}

/**
 * One thing the source says a crawl could be pointed at. Every field is optional because that
 * is how the generated contract types describe them; a scope without a key is not one, and is
 * dropped rather than rendered as a box that saves nothing.
 */
export type ConnectorScopeOption = {
  key?: string
  displayName?: string
  reachable?: boolean
  admissible?: boolean
  instruction?: string | null
}

function Field({
  field,
  value,
  invalid,
  onChange,
  scopes,
}: {
  field: ConnectorField
  value: string | boolean | undefined
  invalid: boolean
  onChange: (name: string, value: string | boolean) => void
  scopes?: ConnectorScopeOption[]
}) {
  const id = `connector-field-${field.name}`
  const describedBy = field.description ? `${id}-description` : undefined

  if (field.type === "scopes") {
    return (
      <ScopeField
        field={field}
        chosen={String(value ?? "")}
        scopes={scopes}
        onChange={onChange}
        describedBy={describedBy}
      />
    )
  }

  if (field.type === "checkbox") {
    return (
      <div className="flex items-center justify-between gap-4 rounded-md border p-3">
        <div className="space-y-0.5">
          <Label htmlFor={id}>{field.label}</Label>
          {field.description ? (
            <p id={describedBy} className="text-xs text-muted-foreground">
              {field.description}
            </p>
          ) : null}
        </div>
        <Switch
          id={id}
          checked={value === true}
          onCheckedChange={(checked: boolean) => onChange(field.name, checked)}
        />
      </div>
    )
  }

  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{field.label}</Label>
      {field.type === "select" ? (
        <Select value={String(value ?? "")} onValueChange={(next: string) => onChange(field.name, next)}>
          <SelectTrigger id={id} className="w-full" aria-describedby={describedBy}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {field.options.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      ) : (
        <Input
          id={id}
          value={String(value ?? "")}
          inputMode={field.type === "number" ? "numeric" : undefined}
          placeholder={field.type === "number" ? undefined : field.placeholder}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          onChange={(event) => onChange(field.name, event.target.value)}
        />
      )}
      {field.description ? (
        <p id={describedBy} className="text-xs text-muted-foreground">
          {field.description}
        </p>
      ) : null}
      {invalid && field.type === "number" ? (
        <p className="text-sm text-destructive">
          {field.label} must be a whole number of at least {field.min ?? 1}.
        </p>
      ) : null}
    </div>
  )
}

/**
 * Picking from what the source holds, with what each choice costs.
 *
 * <p>Three states, and the difference is the reason for listing at all: already readable,
 * readable once chosen because the adapter can let itself in, and readable only after somebody
 * acts at the source — which says what to do rather than leaving a crawl to return nothing.
 */
function ScopeField({
  field,
  chosen,
  scopes,
  onChange,
  describedBy,
}: {
  field: Extract<ConnectorField, { type: "scopes" }>
  chosen: string
  scopes?: ConnectorScopeOption[]
  onChange: (name: string, value: string | boolean) => void
  describedBy?: string
}) {
  const selected = new Set(
    chosen
      .split(",")
      .map((entry) => entry.trim())
      .filter(Boolean),
  )

  function toggle(key: string, on: boolean) {
    const next = new Set(selected)
    if (on) {
      next.add(key)
    } else {
      next.delete(key)
    }
    onChange(field.name, [...next].join(", "))
  }

  return (
    <div className="space-y-2">
      <Label>{field.label}</Label>
      {field.description ? (
        <p id={describedBy} className="text-xs text-muted-foreground">
          {field.description}
        </p>
      ) : null}

      {scopes === undefined ? (
        <p className="rounded-md border border-dashed p-3 text-sm text-muted-foreground">
          Store a credential first — this is read from the source with it.
        </p>
      ) : scopes.length === 0 ? (
        <p className="rounded-md border border-dashed p-3 text-sm text-muted-foreground">
          The source reported nothing to choose from.
        </p>
      ) : (
        <ul className="divide-y rounded-md border">
          {scopes.flatMap((scope) =>
            scope.key
              ? [
                  <li key={scope.key} className="flex items-start justify-between gap-3 p-3">
                    <div className="min-w-0 space-y-0.5">
                      <Label htmlFor={`scope-${scope.key}`} className="font-normal">
                        {scope.displayName ?? scope.key}
                      </Label>
                      {scope.reachable ? null : scope.admissible ? (
                        <p className="text-xs text-muted-foreground">
                          The bot will add itself when this is saved.
                        </p>
                      ) : (
                        <p className="text-xs text-warning-foreground">{scope.instruction}</p>
                      )}
                    </div>
                    <Switch
                      id={`scope-${scope.key}`}
                      checked={selected.has(scope.key)}
                      disabled={!scope.reachable && !scope.admissible}
                      onCheckedChange={(on: boolean) => toggle(scope.key as string, on)}
                    />
                  </li>,
                ]
              : [],
          )}
        </ul>
      )}

      {field.emptyMeans && selected.size === 0 ? (
        <p className="text-xs text-muted-foreground">{field.emptyMeans}</p>
      ) : null}
    </div>
  )
}
