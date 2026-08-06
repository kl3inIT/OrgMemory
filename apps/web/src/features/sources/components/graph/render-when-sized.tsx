import { type ComponentProps, type ReactNode, useLayoutEffect, useRef, useState } from "react"

import { cn } from "@/lib/utils"

export function RenderWhenSized({
  children,
  className,
  ...props
}: Omit<ComponentProps<"div">, "children"> & { children: ReactNode }) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [sized, setSized] = useState(false)

  useLayoutEffect(() => {
    const container = containerRef.current
    if (!container) return

    const update = ({ width, height }: Pick<DOMRectReadOnly, "width" | "height">) => {
      setSized(width > 0 && height > 0)
    }
    update(container.getBoundingClientRect())

    const observer = new ResizeObserver((entries) => {
      const entry = entries[0]
      if (entry) update(entry.contentRect)
    })
    observer.observe(container)
    return () => observer.disconnect()
  }, [])

  return (
    <div ref={containerRef} className={cn("min-h-0 min-w-0", className)} {...props}>
      {sized ? children : null}
    </div>
  )
}
