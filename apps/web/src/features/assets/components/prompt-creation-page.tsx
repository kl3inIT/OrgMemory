import { useMutation, useQuery } from "@tanstack/react-query"
import { Link, useNavigate } from "@tanstack/react-router"
import { ChevronLeft, Plus } from "lucide-react"
import { useState, type FormEvent } from "react"

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
import { PromptTemplateEditor } from "@/features/assets/components/prompt-template-editor"
import {
  buildPromptAssetDraft,
  createEmptyPromptForm,
} from "@/features/assets/prompt-template-form"
import { apiErrorMessage } from "@/lib/api-error"
import {
  createAssetMutation,
  listKnowledgeSpaceUploadTargetsOptions,
} from "@/lib/hey-api/@tanstack/react-query.gen"

export function PromptCreationPage() {
  const navigate = useNavigate()
  const spaces = useQuery(listKnowledgeSpaceUploadTargetsOptions())
  const create = useMutation(createAssetMutation())
  const [value, setValue] = useState(createEmptyPromptForm)
  const [error, setError] = useState<string>()

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const built = buildPromptAssetDraft(value)
    if (!built.ok) {
      setError(built.message)
      return
    }
    setError(undefined)
    try {
      const asset = await create.mutateAsync({ body: built.request })
      if (!asset.id) throw new Error("The created Draft did not include an Asset id.")
      await navigate({ to: "/assets/$assetId/governance", params: { assetId: asset.id } })
    } catch (failure) {
      setError(apiErrorMessage(failure, "The Prompt Draft could not be created."))
    }
  }

  return (
    <PageLayout.Root variant="wide">
      <PageLayout.Header
        title="Create a Prompt"
        description="Author a private Draft, validate its contract, then publish an immutable release."
        breadcrumb={
          <Breadcrumb>
            <BreadcrumbList>
              <BreadcrumbItem><BreadcrumbLink asChild><Link to="/assets">Assets</Link></BreadcrumbLink></BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem><BreadcrumbPage>New Prompt</BreadcrumbPage></BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
        }
        actions={<Button variant="outline" asChild><Link to="/assets"><ChevronLeft aria-hidden="true" />Cancel</Link></Button>}
      />
      <PageLayout.Body>
        <PromptTemplateEditor
          value={value}
          onChange={setValue}
          onSubmit={submit}
          submitLabel={create.isPending ? "Creating Draft" : "Create private Draft"}
          submitting={create.isPending}
          disabled={spaces.isPending || spaces.isError}
          error={error ?? (spaces.isError ? "Creation targets could not be loaded." : undefined)}
          errorAction={!error && spaces.isError ? {
            label: spaces.isFetching ? "Retrying" : "Try again",
            onClick: () => void spaces.refetch(),
            disabled: spaces.isFetching,
          } : undefined}
          spaces={spaces.data}
          spacesLoading={spaces.isPending}
          asideAction={
            <p className="flex items-start gap-2 text-xs leading-5 text-content-muted">
              <Plus className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
              Creation is atomic: the Asset and populated Draft are stored together.
            </p>
          }
        />
      </PageLayout.Body>
    </PageLayout.Root>
  )
}
