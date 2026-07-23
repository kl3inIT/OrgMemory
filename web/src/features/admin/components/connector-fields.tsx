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
  children,
}: {
  descriptor: ConnectorFormDescriptor
  draft: ConnectorFieldDraft
  /** Field names that cannot be saved as typed. */
  invalid: string[]
  onChange: (name: string, value: string | boolean) => void
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

function Field({
  field,
  value,
  invalid,
  onChange,
}: {
  field: ConnectorField
  value: string | boolean | undefined
  invalid: boolean
  onChange: (name: string, value: string | boolean) => void
}) {
  const id = `connector-field-${field.name}`
  const describedBy = field.description ? `${id}-description` : undefined

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
