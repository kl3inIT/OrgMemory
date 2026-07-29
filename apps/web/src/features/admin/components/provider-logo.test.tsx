import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { ProviderLogo } from "@/features/admin/components/provider-logo"
import type { ProviderPresetResponse } from "@/lib/hey-api"

describe("ProviderLogo", () => {
  it.each([
    ["OPENAI", "openai"],
    ["ANTHROPIC", "anthropic"],
    ["NINE_ROUTER", "nine-router"],
    ["OPENROUTER", "openrouter"],
    ["LITELLM", "litellm"],
    ["OLLAMA", "ollama"],
    ["OPENAI_COMPATIBLE", "openai-compatible"],
  ] satisfies [NonNullable<ProviderPresetResponse["preset"]>, string][])(
    "renders the verified %s brand mark",
    (preset, slug) => {
      render(
        <div data-testid="logo-host">
          <ProviderLogo preset={preset} />
        </div>,
      )

      expect(
        screen.getByTestId("logo-host").querySelector(`[data-provider-logo="${slug}"]`),
      ).not.toBeNull()
    },
  )
})
