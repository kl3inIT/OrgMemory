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

/**
 * The Google Drive mark: one triangle folded into three coloured faces.
 *
 * <p>Drawn as three polygons rather than traced paths, because that is what the mark is — the
 * blue lower-right, the green lower-left, the yellow apex — and three polygons stay legible to
 * whoever has to change it.
 */
function GoogleDriveMark({ className }: MarkProps) {
  return (
    <svg viewBox="0 0 87.3 78" className={className} role="img" aria-label="Google Drive">
      <path d="m6.6 66.85 3.85 6.65c.8 1.4 1.95 2.5 3.3 3.3l13.75-23.8h-27.5c0 1.55.4 3.1 1.2 4.5z" fill="#0066da" />
      <path d="m43.65 25-13.75-23.8c-1.35.8-2.5 1.9-3.3 3.3l-25.4 44a9.06 9.06 0 0 0 -1.2 4.5h27.5z" fill="#00ac47" />
      <path d="m73.55 76.8c1.35-.8 2.5-1.9 3.3-3.3l1.6-2.75 7.65-13.25c.8-1.4 1.2-2.95 1.2-4.5h-27.502l5.852 11.5z" fill="#ea4335" />
      <path d="m43.65 25 13.75-23.8c-1.35-.8-2.9-1.2-4.5-1.2h-18.5c-1.6 0-3.15.45-4.5 1.2z" fill="#00832d" />
      <path d="m59.8 53h-32.3l-13.75 23.8c1.35.8 2.9 1.2 4.5 1.2h50.8c1.6 0 3.15-.45 4.5-1.2z" fill="#2684fc" />
      <path d="m73.4 26.5-12.7-22c-.8-1.4-1.95-2.5-3.3-3.3l-13.75 23.8 16.15 28h27.45c0-1.55-.4-3.1-1.2-4.5z" fill="#ffba00" />
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
  "google-drive": GoogleDriveMark,
  upload: UploadMark,
  edge: EdgeMark,
}

export type SourceIconName = keyof typeof MARKS

export function SourceIcon({ name, className }: { name: SourceIconName; className?: string }) {
  const Mark = MARKS[name]
  return <Mark className={className} />
}
