import { uploadSource, type SourceResponse } from "@/lib/hey-api"

export type UploadSourceInput = {
  file: File
  classification: "PUBLIC" | "INTERNAL" | "CONFIDENTIAL" | "RESTRICTED"
  knowledgeSpaceId: string
}

export async function uploadSourceWithCsrf(input: UploadSourceInput): Promise<SourceResponse> {
  const { data } = await uploadSource({
    body: { file: input.file },
    query: {
      classification: input.classification,
      knowledgeSpaceId: input.knowledgeSpaceId,
    },
    throwOnError: true,
  })
  return data
}
