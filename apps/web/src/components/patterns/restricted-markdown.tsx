import { cjk } from "@streamdown/cjk"
import { code } from "@streamdown/code"
import type { ComponentProps } from "react"
import { ErrorBoundary } from "react-error-boundary"
import { Streamdown, defaultUrlTransform } from "streamdown"

const plugins = { cjk, code }

function safeUrlTransform(
  url: string,
  key: string,
  node: Parameters<typeof defaultUrlTransform>[2],
) {
  if (key !== "href") return null
  try {
    const parsed = new URL(url)
    if (!["http:", "https:", "mailto:"].includes(parsed.protocol)) return null
    return defaultUrlTransform(url, key, node)
  } catch {
    return null
  }
}

function ConfirmedLink({ href, children }: ComponentProps<"a"> & { node?: unknown }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      onClick={(event) => {
        if (!href || !window.confirm(`Open this external link?\n\n${href}`)) {
          event.preventDefault()
        }
      }}
    >
      {children}
    </a>
  )
}

export function RestrictedMarkdown({ content }: { content: string }) {
  return (
    <div data-testid="restricted-source-markdown" className="size-full">
      <ErrorBoundary
        fallbackRender={() => (
          <pre className="min-h-full whitespace-pre-wrap p-6 font-mono text-sm leading-relaxed text-foreground">
            {content}
          </pre>
        )}
      >
        <Streamdown
          className="size-full px-6 py-5 [&>*:first-child]:mt-0 [&>*:last-child]:mb-0"
          mode="static"
          plugins={plugins}
          skipHtml
          urlTransform={safeUrlTransform}
          components={{
            a: ConfirmedLink,
            img: ({ alt }: ComponentProps<"img"> & { node?: unknown }) => (
              <span role="img" aria-label={alt}>
                [Remote image blocked]
              </span>
            ),
          }}
        >
          {content}
        </Streamdown>
      </ErrorBoundary>
    </div>
  )
}
