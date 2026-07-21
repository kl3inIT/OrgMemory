import { uploadSource, type SourceResponse } from "@/lib/hey-api"

export type UploadSourceInput = {
  file: File
  classification: "INTERNAL" | "CONFIDENTIAL"
}

export async function uploadSourceWithCsrf(input: UploadSourceInput): Promise<SourceResponse> {
  const { data } = await uploadSource({
    body: { file: input.file },
    query: { classification: input.classification },
    throwOnError: true,
  })
  return data
}
