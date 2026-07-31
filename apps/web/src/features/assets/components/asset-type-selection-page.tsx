import { Link } from "@tanstack/react-router"
import { Check, ChevronLeft } from "lucide-react"
import { useState } from "react"

import { PageLayout } from "@/components/layouts/page-layout"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import {
  ASSET_TYPE_META,
  type AssetType,
} from "@/features/assets/asset-format"
import { cn } from "@/lib/utils"

const AUTHORING_OPTIONS: Array<{
  type: AssetType
  description: string
}> = [
  {
    type: "SKILL",
    description: "A packaged capability that an AI assistant can install and use.",
  },
  {
    type: "PROMPT_TEMPLATE",
    description: "A reusable prompt template with variables and guidance.",
  },
  {
    type: "WORK_INSTRUCTION",
    description: "A governed procedure people and AI can follow.",
  },
  {
    type: "CAPABILITY_PACK",
    description: "A coordinated set of assets for one outcome.",
  },
]

const SKILL_REQUIREMENTS = [
  "Folder or ZIP package",
  "SKILL.md required",
  "Files are validated before a Draft is created",
]

export function AssetTypeSelectionPage() {
  const [selectedType, setSelectedType] = useState<AssetType>("SKILL")
  const selected = ASSET_TYPE_META[selectedType]

  return (
    <PageLayout.Root variant="wide">
      <PageLayout.Header
        title="Add an asset"
        description="Choose what your organization wants to make reusable."
        breadcrumb={
          <Breadcrumb>
            <BreadcrumbList>
              <BreadcrumbItem>
                <BreadcrumbLink asChild>
                  <Link to="/assets">Assets</Link>
                </BreadcrumbLink>
              </BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem>
                <BreadcrumbPage>Add asset</BreadcrumbPage>
              </BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
        }
      />

      <PageLayout.Body>
        <div
          role="group"
          aria-label="Choose an Asset type"
          className="grid gap-4 md:grid-cols-2"
        >
          {AUTHORING_OPTIONS.map((option) => {
            const meta = ASSET_TYPE_META[option.type]
            const Icon = meta.icon
            const selectedOption = selectedType === option.type

            return (
              <button
                key={option.type}
                type="button"
                aria-pressed={selectedOption}
                onClick={() => setSelectedType(option.type)}
                className="rounded-xl text-left outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
              >
                <Card
                  className={cn(
                    "h-full gap-4 bg-surface-raised py-0 shadow-none transition-[border-color,background-color,box-shadow] hover:border-border-strong hover:bg-surface-subtle",
                    selectedOption &&
                      "border-action-primary bg-status-success-surface/30 shadow-sm hover:border-action-primary hover:bg-status-success-surface/30",
                  )}
                >
                  <CardContent className="flex min-h-44 flex-col p-6">
                    <div className="flex items-start justify-between gap-4">
                      <span className={cn("grid size-10 place-items-center rounded-xl", meta.tone)}>
                        <Icon className="size-5" strokeWidth={1.9} aria-hidden="true" />
                      </span>
                      {selectedOption ? (
                        <span className="grid size-6 place-items-center rounded-full bg-action-primary text-action-primary-foreground">
                          <Check className="size-4" aria-hidden="true" />
                          <span className="sr-only">Selected</span>
                        </span>
                      ) : null}
                    </div>
                    <h2 className="mt-6 text-section-title text-content-primary">
                      {meta.label}
                    </h2>
                    <p className="mt-2 max-w-xl text-sm text-content-secondary">
                      {option.description}
                    </p>
                  </CardContent>
                </Card>
              </button>
            )
          })}
        </div>

        <Card className="gap-0 bg-surface-subtle py-0 shadow-none">
          <CardContent className="p-5 sm:p-6">
            <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-base font-semibold text-content-primary">
                    {selectedType === "SKILL"
                      ? "Skill package requirements"
                      : `${selected.label} authoring`}
                  </h2>
                </div>
                {selectedType === "SKILL" ? (
                  <ul className="mt-4 grid gap-2 text-sm text-content-secondary sm:grid-cols-3 sm:gap-5">
                    {SKILL_REQUIREMENTS.map((requirement) => (
                      <li key={requirement} className="flex items-center gap-2">
                        <Check className="size-4 shrink-0 text-status-success-content" aria-hidden="true" />
                        {requirement}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="mt-2 text-sm text-content-secondary">
                    Browser authoring for this Asset type is not available yet.
                  </p>
                )}
              </div>
              <p className="max-w-md text-sm text-content-muted lg:text-right">
                This step only chooses an Asset type. It does not create a Draft or upload content.
              </p>
            </div>
          </CardContent>
        </Card>

        <div className="flex border-t border-border-subtle pt-5">
          <Button variant="outline" asChild>
            <Link to="/assets">
              <ChevronLeft aria-hidden="true" />
              Back to Assets
            </Link>
          </Button>
        </div>
      </PageLayout.Body>
    </PageLayout.Root>
  )
}
