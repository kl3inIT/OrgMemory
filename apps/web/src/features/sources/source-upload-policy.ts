const MAX_FILE_SIZE_BY_EXTENSION: Record<string, number> = {
  csv: 10,
  doc: 25,
  docx: 25,
  htm: 10,
  html: 10,
  md: 10,
  odp: 25,
  ods: 15,
  odt: 25,
  pdf: 25,
  ppt: 25,
  pptx: 25,
  rtf: 10,
  txt: 10,
  xls: 15,
  xlsx: 15,
}

export const ACCEPTED_EXTENSIONS = Object.keys(MAX_FILE_SIZE_BY_EXTENSION)
export const ACCEPTED_FILE_TYPES = ACCEPTED_EXTENSIONS.map((extension) => `.${extension}`).join(",")

export function sourceUploadFileError(file: File): string | undefined {
  const extension = file.name.split(".").pop()?.toLowerCase() ?? ""
  const maximumMegabytes = MAX_FILE_SIZE_BY_EXTENSION[extension]
  if (!maximumMegabytes) return "That file type is not supported."
  if (file.size > maximumMegabytes * 1024 * 1024) {
    return `The .${extension} file must be ${maximumMegabytes} MB or smaller.`
  }
  return undefined
}
