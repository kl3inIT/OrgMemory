import anthropicLogo from "@lobehub/icons-static-svg/icons/anthropic.svg"
import ollamaLogo from "@lobehub/icons-static-svg/icons/ollama.svg"
import openAiLogo from "@lobehub/icons-static-svg/icons/openai.svg"
import openRouterLogo from "@lobehub/icons-static-svg/icons/openrouter.svg"
import { Unplug } from "lucide-react"

import liteLlmLogo from "@/assets/ai-providers/litellm.svg"
import nineRouterLogo from "@/assets/ai-providers/nine-router.svg"
import type { ProviderPresetResponse } from "@/lib/hey-api"
import { cn } from "@/lib/utils"

type ProviderPreset = ProviderPresetResponse["preset"]

const PROVIDER_LOGOS: Partial<
  Record<NonNullable<ProviderPreset>, { src: string; slug: string; monochrome?: boolean }>
> = {
  OPENAI: { src: openAiLogo, slug: "openai", monochrome: true },
  ANTHROPIC: { src: anthropicLogo, slug: "anthropic", monochrome: true },
  NINE_ROUTER: { src: nineRouterLogo, slug: "nine-router" },
  OPENROUTER: { src: openRouterLogo, slug: "openrouter", monochrome: true },
  LITELLM: { src: liteLlmLogo, slug: "litellm" },
  OLLAMA: { src: ollamaLogo, slug: "ollama", monochrome: true },
}

export function ProviderLogo({
  preset,
  className,
}: {
  preset?: ProviderPreset
  className?: string
}) {
  const logo = preset ? PROVIDER_LOGOS[preset] : undefined

  if (!logo) {
    return (
      <Unplug
        data-provider-logo="openai-compatible"
        className={cn("size-5", className)}
        aria-hidden="true"
      />
    )
  }

  return (
    <img
      src={logo.src}
      alt=""
      data-provider-logo={logo.slug}
      className={cn(
        "size-5 object-contain",
        logo.monochrome && "dark:invert",
        className,
      )}
      aria-hidden="true"
    />
  )
}
