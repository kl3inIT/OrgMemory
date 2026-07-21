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

const MAX_FILE_SIZE = 25 * 1024 * 1024

export function SourceUploadDialog({
  pending,
  onUpload,
}: {
  pending: boolean
  onUpload: (input: UploadSourceInput) => Promise<void>
}) {
  const [open, setOpen] = useState(false)
  const [file, setFile] = useState<File>()
  const [classification, setClassification] = useState<UploadSourceInput["classification"]>("CONFIDENTIAL")
  const [error, setError] = useState<string>()

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!file) {
      setError("Choose a file to upload.")
      return
    }
    if (file.size > MAX_FILE_SIZE) {
      setError("The file must be 25 MB or smaller.")
      return
    }
    setError(undefined)
    try {
      await onUpload({ file, classification })
      setOpen(false)
      setFile(undefined)
      setClassification("CONFIDENTIAL")
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The upload could not be completed.")
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
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
              onValueChange={(value: string) => setClassification(value as UploadSourceInput["classification"])}
            >
              <SelectTrigger id="source-classification" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="CONFIDENTIAL">Confidential · my department</SelectItem>
                <SelectItem value="INTERNAL">Internal · all employees</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {error ? (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}

          <DialogFooter>
            <Button type="button" variant="outline" disabled={pending} onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={pending}>
              {pending ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <Upload aria-hidden="true" />}
              {pending ? "Uploading" : "Upload"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
