"use client";

import { cjk } from "@streamdown/cjk";
import { code } from "@streamdown/code";
import { math } from "@streamdown/math";
import { mermaid } from "@streamdown/mermaid";
import type { ComponentProps } from "react";
import { memo, useMemo } from "react";
import { Streamdown } from "streamdown";

import { cn } from "@/lib/utils";

export type MessageResponseProps = ComponentProps<typeof Streamdown>;

const pluginSets = {
  plain: { cjk },
  code: { cjk, code },
  math: { cjk, math },
  codeMath: { cjk, code, math },
  mermaid: { cjk, code, mermaid },
  mermaidMath: { cjk, code, math, mermaid },
} as const;

export function pluginsFor(content: unknown) {
  const markdown = typeof content === "string" ? content : "";
  const hasMermaid = /^(?: {0,3})(?:`{3,}|~{3,})[ \t]*mermaid(?:[ \t]|$)/im.test(markdown);
  const hasCode = hasMermaid || /^(?: {0,3})(?:`{3,}|~{3,})(?:[ \t]|$)/m.test(markdown);
  const hasMath = /\$|\\\(|\\\[/.test(markdown);
  if (hasMermaid) return hasMath ? pluginSets.mermaidMath : pluginSets.mermaid;
  if (hasCode) return hasMath ? pluginSets.codeMath : pluginSets.code;
  return hasMath ? pluginSets.math : pluginSets.plain;
}

export const MessageResponse = memo(
  ({ className, children, ...props }: MessageResponseProps) => {
    const plugins = useMemo(() => pluginsFor(children), [children]);
    return (
      <Streamdown
        className={cn(
          "size-full [&>*:first-child]:mt-0 [&>*:last-child]:mb-0",
          className
        )}
        plugins={plugins}
        {...props}
      >
        {children}
      </Streamdown>
    );
  }
);

MessageResponse.displayName = "MessageResponse";
