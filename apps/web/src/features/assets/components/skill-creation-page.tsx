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
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { cn } from "@/lib/utils"

const CREATION_METHODS = [
  {
    title: "Start from scratch",
    description: "Write the instructions and add supporting files in OrgMemory.",
    icon: PencilLine,
    tone: "bg-status-info-surface text-status-info-content",
    available: false,
  },
  {
    title: "Upload a skill",
    description: "Create a governed Draft from an existing Skill package.",
    icon: FileUp,
    tone: "bg-status-success-surface text-status-success-content",
    available: true,
  },
  {
    title: "Import from GitHub",
    description: "Preview and import one or more Skills from a repository.",
    icon: GitFork,
    tone: "bg-surface-subtle text-content-primary",
    available: false,
  },
] as const

export function SkillCreationPage() {
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
            const card = (
              <Card
                className={cn(
                  "h-full min-h-56 gap-0 bg-surface-raised py-0 shadow-none transition-[border-color,background-color,transform,box-shadow]",
                  method.available
                    ? "hover:-translate-y-0.5 hover:border-border-strong hover:bg-surface-subtle hover:shadow-sm"
                    : "opacity-65",
                )}
              >
                <CardContent className="flex h-full flex-col p-6">
                  <div className="flex items-start justify-between gap-4">
                    <span className={cn("grid size-10 place-items-center rounded-xl", method.tone)}>
                      <Icon className="size-5" strokeWidth={1.9} aria-hidden="true" />
                    </span>
                    {!method.available ? <Badge variant="outline">Soon</Badge> : null}
                  </div>
                  <h2 className="mt-7 text-section-title text-content-primary">{method.title}</h2>
                  <p className="mt-2 text-sm leading-6 text-content-secondary">{method.description}</p>
                  {method.available ? (
                    <span className="mt-auto flex items-center justify-between pt-6 text-label text-content-primary">
                      Continue
                      <ArrowRight className="size-4" aria-hidden="true" />
                    </span>
                  ) : null}
                </CardContent>
              </Card>
            )

            return method.available ? (
              <Link
                key={method.title}
                to="/assets/new/skill/upload"
                className="rounded-xl outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
              >
                {card}
              </Link>
            ) : (
              <div key={method.title} aria-disabled="true">{card}</div>
            )
          })}
        </section>

        <Card className="gap-0 border-dashed bg-surface-subtle py-0 shadow-none">
          <CardContent className="p-5 text-sm text-content-secondary sm:p-6">
            Every path creates a private Draft first. Publishing remains a separate action in the
            Asset governance workspace.
          </CardContent>
        </Card>
      </PageLayout.Body>
    </PageLayout.Root>
  )
}
