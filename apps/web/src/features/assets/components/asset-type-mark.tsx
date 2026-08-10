import type { ComponentProps } from "react"

import type { AssetType } from "@/features/assets/asset-format"
import { cn } from "@/lib/utils"

type AssetTypeMarkProps = ComponentProps<"svg"> & {
  type: AssetType
}

export function AssetTypeMark({ type, className, ...props }: AssetTypeMarkProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={cn("size-5", className)}
      {...props}
    >
      {type === "SKILL" ? <SkillMark /> : null}
      {type === "PROMPT_TEMPLATE" ? <PromptMark /> : null}
      {type === "WORK_INSTRUCTION" ? <InstructionMark /> : null}
      {type === "CAPABILITY_PACK" ? <PackMark /> : null}
    </svg>
  )
}

export function AssetCatalogMark({ className, ...props }: ComponentProps<"svg">) {
  return (
    <svg
      viewBox="0 0 28 28"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={cn("size-7", className)}
      {...props}
    >
      <path
        d="m14 3.5 9 5.1-9 5.1-9-5.1 9-5.1Z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path
        d="m5 13.1 9 5.1 9-5.1M5 17.7l9 5.1 9-5.1"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M14 8.4v5.3"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
    </svg>
  )
}

function SkillMark() {
  return (
    <>
      <path
        d="m12 2.8 7.8 4.5v9L12 20.8l-7.8-4.5v-9L12 2.8Z"
        stroke="currentColor"
        strokeWidth="1.65"
        strokeLinejoin="round"
      />
      <path
        d="m8.2 9.1 3.8-2.2 3.8 2.2v4.4L12 15.7l-3.8-2.2V9.1Z"
        stroke="currentColor"
        strokeWidth="1.65"
        strokeLinejoin="round"
      />
      <path d="M12 10.2v2.2" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" />
    </>
  )
}

function PromptMark() {
  return (
    <>
      <path
        d="M4 5.2h16v10.7H9l-5 4V5.2Z"
        stroke="currentColor"
        strokeWidth="1.65"
        strokeLinejoin="round"
      />
      <path d="M8 9h8M8 12.2h5" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" />
      <path d="m17.7 2.8.45 1.05 1.05.45-1.05.45-.45 1.05-.45-1.05-1.05-.45 1.05-.45.45-1.05Z" fill="currentColor" />
    </>
  )
}

function InstructionMark() {
  return (
    <>
      <path
        d="M6.2 3.5h11.6v17H6.2v-17Z"
        stroke="currentColor"
        strokeWidth="1.65"
        strokeLinejoin="round"
      />
      <path d="M9.2 8h5.6M9.2 12h5.6M9.2 16h3.2" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" />
      <path d="m3.5 8.4 1.2 1.2 2.1-2.3M3.5 12.4l1.2 1.2 2.1-2.3" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" />
    </>
  )
}

function PackMark() {
  return (
    <>
      <path
        d="m12 3 8 4.4-8 4.4-8-4.4L12 3Z"
        stroke="currentColor"
        strokeWidth="1.65"
        strokeLinejoin="round"
      />
      <path d="m4 11.5 8 4.4 8-4.4M4 15.7l8 4.4 8-4.4" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" />
    </>
  )
}
