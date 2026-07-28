import { useEffect, useRef, useState } from "react"

const MINIMUM_VISIBLE_MS = 500

export function useAssistantThinkingVisibility(active: boolean) {
  const [visible, setVisible] = useState(active)
  const startedAt = useRef<number | null>(active ? performance.now() : null)

  useEffect(() => {
    if (active) {
      startedAt.current ??= performance.now()
      setVisible(true)
      return
    }

    if (startedAt.current === null) {
      setVisible(false)
      return
    }

    const elapsed = performance.now() - startedAt.current
    const remaining = Math.max(0, MINIMUM_VISIBLE_MS - elapsed)
    const timeout = window.setTimeout(() => {
      startedAt.current = null
      setVisible(false)
    }, remaining)

    return () => window.clearTimeout(timeout)
  }, [active])

  return visible
}
