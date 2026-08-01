import { Link } from "@tanstack/react-router"
import { ArrowRight, FileUp, GitFork, PencilLine } from "lucide-react"

import { PageLayout } from "@/components/layouts/page-layout"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { Card, CardContent } from "@/components/ui/card"
import { buildSkillDraftHandoff } from "@/features/assets/agent-handoff/skill-agent-handoffs"
import { AgentHandoffPanel } from "@/features/assets/components/agent-handoff-panel"
import { cn } from "@/lib/utils"

const CREATION_METHODS = [
  {
    title: "Start from scratch",
    description: "Write the instructions and add supporting files in OrgMemory.",
    icon: PencilLine,
    tone: "bg-status-info-surface text-status-info-content",
    to: "/assets/new/skill/scratch",
  },
  {
    title: "Upload a skill",
    description: "Create a governed Draft from an existing Skill package.",
    icon: FileUp,
    tone: "bg-status-success-surface text-status-success-content",
    to: "/assets/new/skill/upload",
  },
  {
    title: "Import from GitHub",
    description: "Preview and import one or more Skills from a repository.",
    icon: GitFork,
    tone: "bg-status-info-surface text-status-info-content",
    to: "/assets/new/skill/github",
  },
] as const

export function SkillCreationPage() {
  const draftHandoff = buildSkillDraftHandoff()

  return (
    <PageLayout.Root variant="wide">
      <PageLayout.Header
        title="Create a Skill"
        description="Choose how you want to bring a reusable capability into your organization."
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
                <BreadcrumbPage>Create Skill</BreadcrumbPage>
              </BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
        }
      />

      <PageLayout.Body>
        <section className="grid gap-4 lg:grid-cols-3" aria-label="Skill creation methods">
          {CREATION_METHODS.map((method) => {
            const Icon = method.icon
            return (
              <Link
                key={method.title}
                to={method.to}
                className="rounded-xl outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
              >
                <Card
                  className={cn(
                    "h-full gap-0 bg-surface-raised py-0 shadow-none transition-[border-color,background-color,transform,box-shadow]",
                    "hover:-translate-y-0.5 hover:border-border-strong hover:bg-surface-subtle hover:shadow-sm",
                  )}
                >
                  <CardContent className="relative flex h-full items-start gap-4 p-4 lg:min-h-40 lg:flex-col lg:gap-0 lg:p-5">
                    <span
                      className={cn(
                        "grid size-9 shrink-0 place-items-center rounded-lg",
                        method.tone,
                      )}
                    >
                      <Icon className="size-4.5" strokeWidth={1.9} aria-hidden="true" />
                    </span>
                    <div className="min-w-0 pr-6 lg:mt-5 lg:pr-0">
                      <h2 className="text-section-title text-content-primary">{method.title}</h2>
                      <p className="mt-1 text-sm leading-6 text-content-secondary lg:mt-2">
                        {method.description}
                      </p>
                    </div>
                    <ArrowRight
                      className="absolute top-4 right-4 size-4 text-content-muted lg:top-5 lg:right-5"
                      aria-hidden="true"
                    />
                  </CardContent>
                </Card>
              </Link>
            )
          })}
        </section>

        <AgentHandoffPanel handoff={draftHandoff} />
      </PageLayout.Body>
    </PageLayout.Root>
  )
}
