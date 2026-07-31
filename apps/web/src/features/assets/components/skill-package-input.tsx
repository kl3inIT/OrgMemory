import { useMutation } from "@tanstack/react-query"
import { FileArchive, FolderOpen, LoaderCircle } from "lucide-react"
import { useState } from "react"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { normalizeSelectedSkillPackage } from "@/features/assets/skill-package-browser"
import { apiErrorMessage } from "@/lib/api-error"
import type { SkillPackageInspection } from "@/lib/hey-api"
import { inspectSkillPackageMutation } from "@/lib/hey-api/@tanstack/react-query.gen"

export function SkillPackageInput({
  disabled,
  onInspected,
}: {
  disabled?: boolean
  onInspected: (file: File, inspection: SkillPackageInspection) => void
}) {
  const inspect = useMutation(inspectSkillPackageMutation())
  const [error, setError] = useState<string>()

  async function inspectFiles(files: File[]) {
    if (files.length === 0) return
    setError(undefined)
    try {
      const archive = await normalizeSelectedSkillPackage(files)
      const result = await inspect.mutateAsync({ body: { file: archive } })
      onInspected(archive, result)
    } catch (failure) {
      setError(apiErrorMessage(failure, "The Skill package could not be inspected."))
    }
  }

  const pending = disabled || inspect.isPending
  return (
    <div className="space-y-5">
      <div className="space-y-2">
        <Label htmlFor="skill-package-file">SKILL.md or ZIP</Label>
        <Input
          id="skill-package-file"
          type="file"
          accept=".md,.zip,text/markdown,application/zip"
          disabled={pending}
          onChange={(event) => void inspectFiles(Array.from(event.currentTarget.files ?? []))}
        />
      </div>

      <div className="flex items-center gap-3" aria-hidden="true">
        <span className="h-px flex-1 bg-border-subtle" />
        <span className="text-metadata text-content-muted">or</span>
        <span className="h-px flex-1 bg-border-subtle" />
      </div>

      <div className="space-y-2">
        <Label htmlFor="skill-package-folder">Skill folder</Label>
        <Input
          id="skill-package-folder"
          type="file"
          multiple
          disabled={pending}
          ref={(element) => {
            if (element) element.setAttribute("webkitdirectory", "")
          }}
          onChange={(event) => void inspectFiles(Array.from(event.currentTarget.files ?? []))}
        />
        <p className="text-xs leading-5 text-content-muted">
          Files are packaged in your browser, then validated by the server. Nothing is executed.
        </p>
      </div>

      {inspect.isPending ? (
        <div className="flex items-center gap-2 text-sm text-content-secondary" role="status">
          <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
          Inspecting package
        </div>
      ) : null}
      {error ? (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : null}
      {!inspect.isPending ? (
        <div className="grid grid-cols-2 gap-3 text-xs text-content-muted">
          <span className="flex items-center gap-2">
            <FileArchive className="size-4" aria-hidden="true" />20 MiB ZIP
          </span>
          <span className="flex items-center gap-2">
            <FolderOpen className="size-4" aria-hidden="true" />300 files
          </span>
        </div>
      ) : null}
      {inspect.isError ? (
        <Button type="button" variant="outline" size="sm" onClick={() => inspect.reset()}>
          Choose another package
        </Button>
      ) : null}
    </div>
  )
}
