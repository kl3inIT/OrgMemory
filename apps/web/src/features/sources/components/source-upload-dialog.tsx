import { LoaderCircle, Upload } from "lucide-react"
import { useState, type FormEvent } from "react"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { UploadSourceInput } from "@/features/sources/api/upload-source"
import type { KnowledgeSpaceResponse } from "@/lib/hey-api"

const MAX_FILE_SIZE = 25 * 1024 * 1024

export function SourceUploadDialog({
  pending,
  spaces,
  spacesPending,
  spacesError,
  onRetrySpaces,
  onUpload,
}: {
  pending: boolean
  spaces: KnowledgeSpaceResponse[]
  spacesPending: boolean
  spacesError: boolean
  onRetrySpaces: () => void
  onUpload: (input: UploadSourceInput) => Promise<void>
}) {
  const [open, setOpen] = useState(false)
  const [file, setFile] = useState<File>()
  const [classification, setClassification] = useState<UploadSourceInput["classification"]>("CONFIDENTIAL")
  const [knowledgeSpaceId, setKnowledgeSpaceId] = useState("")
  const [error, setError] = useState<string>()
  const availableSpaces = spaces.filter(
    (space): space is KnowledgeSpaceResponse & { id: string; name: string } =>
      Boolean(space.id && space.name) && (classification === "INTERNAL" || Boolean(space.departmentId)),
  )

  function reset() {
    setFile(undefined)
    setClassification("CONFIDENTIAL")
    setKnowledgeSpaceId("")
    setError(undefined)
  }

  function changeOpen(nextOpen: boolean) {
    if (!nextOpen && pending) return
    setOpen(nextOpen)
    if (!nextOpen) reset()
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!file) {
      setError("Choose a file to upload.")
      return
    }
    if (!knowledgeSpaceId) {
      setError("Choose a Knowledge Space.")
      return
    }
    if (file.size > MAX_FILE_SIZE) {
      setError("The file must be 25 MB or smaller.")
      return
    }
    setError(undefined)
    try {
      await onUpload({ file, classification, knowledgeSpaceId })
      setOpen(false)
      reset()
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The upload could not be completed.")
    }
  }

  return (
    <Dialog open={open} onOpenChange={changeOpen}>
      <DialogTrigger asChild>
        <Button>
          <Upload aria-hidden="true" />
          Upload document
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form className="space-y-5" onSubmit={submit}>
          <DialogHeader>
            <DialogTitle>Upload a document</DialogTitle>
            <DialogDescription>PDF, DOCX, PPTX, TXT, or Markdown up to 25 MB.</DialogDescription>
          </DialogHeader>

          <div className="space-y-2">
            <Label htmlFor="source-file">File</Label>
            <Input
              id="source-file"
              type="file"
              accept=".pdf,.docx,.pptx,.txt,.md"
              required
              disabled={pending}
              onChange={(event) => {
                setFile(event.target.files?.[0])
                setError(undefined)
              }}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="source-classification">Classification</Label>
            <Select
              value={classification}
              disabled={pending}
              onValueChange={(value: string) => {
                const next = value as UploadSourceInput["classification"]
                setClassification(next)
                const selected = spaces.find((space) => space.id === knowledgeSpaceId)
                if (next === "CONFIDENTIAL" && !selected?.departmentId) setKnowledgeSpaceId("")
              }}
            >
              <SelectTrigger id="source-classification" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="PUBLIC">Public · all employees</SelectItem>
                <SelectItem value="CONFIDENTIAL">Confidential · my department</SelectItem>
                <SelectItem value="INTERNAL">Internal · all employees</SelectItem>
                <SelectItem value="RESTRICTED">Restricted · executives only</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="source-space">Knowledge Space</Label>
            {spacesPending ? (
              <div className="flex h-9 items-center gap-2 text-sm text-muted-foreground" role="status">
                <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
                Loading available spaces
              </div>
            ) : null}
            {spacesError ? (
              <Alert variant="destructive">
                <AlertDescription className="flex items-center justify-between gap-3">
                  Available spaces could not be loaded.
                  <Button type="button" variant="outline" size="sm" onClick={onRetrySpaces}>
                    Try again
                  </Button>
                </AlertDescription>
              </Alert>
            ) : null}
            {!spacesPending && !spacesError ? (
              <Select value={knowledgeSpaceId} disabled={pending} onValueChange={setKnowledgeSpaceId}>
                <SelectTrigger id="source-space" className="w-full">
                  <SelectValue placeholder="Choose a space" />
                </SelectTrigger>
                <SelectContent>
                  {availableSpaces.map((space) => (
                    <SelectItem key={space.id} value={space.id}>
                      {space.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : null}
            {!spacesPending && !spacesError && availableSpaces.length === 0 ? (
              <p className="text-sm text-muted-foreground">You do not have permission to upload to this scope.</p>
            ) : null}
          </div>

          {error ? (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}

          <DialogFooter>
            <Button type="button" variant="outline" disabled={pending} onClick={() => changeOpen(false)}>
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={pending || spacesPending || spacesError || availableSpaces.length === 0}
            >
              {pending ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <Upload aria-hidden="true" />}
              {pending ? "Uploading" : "Upload"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
