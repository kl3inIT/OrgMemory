import { Link } from "@tanstack/react-router"
import { ChevronDown, Plus } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { ASSET_TYPE_META, type AssetType } from "@/features/assets/asset-format"

const CREATE_OPTIONS: Array<{
  type: AssetType
  description: string
  available: boolean
  to?: "/assets/new/skill"
}> = [
  {
    type: "SKILL",
    description: "Package instructions and supporting files for AI assistants.",
    available: true,
    to: "/assets/new/skill",
  },
  {
    type: "PROMPT_TEMPLATE",
    description: "Reuse a governed prompt with typed variables.",
    available: false,
  },
  {
    type: "WORK_INSTRUCTION",
    description: "Document a procedure people and AI can follow.",
    available: false,
  },
  {
    type: "CAPABILITY_PACK",
    description: "Coordinate several Assets around one outcome.",
    available: false,
  },
]

export function AssetCreateMenu() {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button size="lg">
          <Plus aria-hidden="true" />
          Add asset
          <ChevronDown className="size-4" aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80 p-1.5">
        {CREATE_OPTIONS.map((option) => {
          const meta = ASSET_TYPE_META[option.type]
          const Icon = meta.icon
          const content = (
            <>
              <span className={`mt-0.5 grid size-9 shrink-0 place-items-center rounded-lg ${meta.tone}`}>
                <Icon className="size-[18px]" strokeWidth={1.9} aria-hidden="true" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex items-center justify-between gap-3 font-semibold text-content-primary">
                  {meta.label}
                  {!option.available ? (
                    <span className="text-metadata font-medium text-content-muted">Later</span>
                  ) : null}
                </span>
                <span className="mt-0.5 block text-xs leading-5 text-content-secondary">
                  {option.description}
                </span>
              </span>
            </>
          )

          return option.available && option.to ? (
            <DropdownMenuItem key={option.type} asChild className="items-start p-3">
              <Link to={option.to}>{content}</Link>
            </DropdownMenuItem>
          ) : (
            <DropdownMenuItem key={option.type} disabled className="items-start p-3">
              {content}
            </DropdownMenuItem>
          )
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
