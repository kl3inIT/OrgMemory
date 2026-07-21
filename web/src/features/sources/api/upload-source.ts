import { getBrowserCsrfHeader } from "@/features/session/csrf-fetch"
import { uploadSource, type SourceResponse } from "@/lib/hey-api"

export type UploadSourceInput = {
  file: File
  classification: "INTERNAL" | "CONFIDENTIAL"
}

export async function uploadSourceWithCsrf(input: UploadSourceInput): Promise<SourceResponse> {
  const [headerName, token] = await getBrowserCsrfHeader()

  const { data } = await uploadSource({
    body: { file: input.file },
    query: { classification: input.classification },
    headers: { [headerName]: token },
    throwOnError: true,
  })
  return data
}
