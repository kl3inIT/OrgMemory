import { MonitorSmartphone, Upload } from "lucide-react"

/**
 * A source's mark, drawn inline.
 *
 * <p>Onyx keeps the same seam — one `SourceIcon` looking a name up in a registry, so no screen
 * decides what a source looks like. The marks are inline SVG rather than files fetched at
 * runtime: a brand mark that arrives over the network is a request that can be blocked, a
 * flash of nothing while it loads, and one more thing to serve. Inline, it renders with the
 * page.
 *
 * <p>A brand mark keeps the brand's colours. The rest are drawn in `currentColor`, because
 * they are not brands — they are things OrgMemory does.
 */

type MarkProps = { className?: string }

/**
 * The Slack mark, in Slack's colours.
 *
 * <p>Reproduced as published rather than recoloured to match the interface. A logo an
 * administrator recognises at a glance is the whole reason to use one instead of a generic
 * chat glyph, and a recoloured logo is neither.
 */
function SlackMark({ className }: MarkProps) {
  return (
    <svg viewBox="0 0 122.8 122.8" className={className} role="img" aria-label="Slack">
      <path
        d="M25.8 77.6c0 7.1-5.8 12.9-12.9 12.9S0 84.7 0 77.6s5.8-12.9 12.9-12.9h12.9v12.9zm6.5 0c0-7.1 5.8-12.9 12.9-12.9s12.9 5.8 12.9 12.9v32.3c0 7.1-5.8 12.9-12.9 12.9s-12.9-5.8-12.9-12.9V77.6z"
        fill="#E01E5A"
      />
      <path
        d="M45.2 25.8c-7.1 0-12.9-5.8-12.9-12.9S38.1 0 45.2 0s12.9 5.8 12.9 12.9v12.9H45.2zm0 6.5c7.1 0 12.9 5.8 12.9 12.9s-5.8 12.9-12.9 12.9H12.9C5.8 58.1 0 52.3 0 45.2s5.8-12.9 12.9-12.9h32.3z"
        fill="#36C5F0"
      />
      <path
        d="M97 45.2c0-7.1 5.8-12.9 12.9-12.9s12.9 5.8 12.9 12.9-5.8 12.9-12.9 12.9H97V45.2zm-6.5 0c0 7.1-5.8 12.9-12.9 12.9s-12.9-5.8-12.9-12.9V12.9C64.7 5.8 70.5 0 77.6 0s12.9 5.8 12.9 12.9v32.3z"
        fill="#2EB67D"
      />
      <path
        d="M77.6 97c7.1 0 12.9 5.8 12.9 12.9s-5.8 12.9-12.9 12.9-12.9-5.8-12.9-12.9V97h12.9zm0-6.5c-7.1 0-12.9-5.8-12.9-12.9s5.8-12.9 12.9-12.9h32.3c7.1 0 12.9 5.8 12.9 12.9s-5.8 12.9-12.9 12.9H77.6z"
        fill="#ECB22E"
      />
    </svg>
  )
}

function UploadMark({ className }: MarkProps) {
  return <Upload className={className} aria-hidden="true" />
}

function EdgeMark({ className }: MarkProps) {
  return <MonitorSmartphone className={className} aria-hidden="true" />
}

const MARKS = {
  slack: SlackMark,
  upload: UploadMark,
  edge: EdgeMark,
}

export type SourceIconName = keyof typeof MARKS

export function SourceIcon({ name, className }: { name: SourceIconName; className?: string }) {
  const Mark = MARKS[name]
  return <Mark className={className} />
}
