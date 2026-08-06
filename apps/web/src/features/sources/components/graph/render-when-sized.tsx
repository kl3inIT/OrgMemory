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

    const update = (target: HTMLElement) => {
      setSized(target.offsetWidth > 0 && target.offsetHeight > 0)
    }
    update(container)

    const observer = new ResizeObserver((entries) => {
      const target = entries[0]?.target
      if (target instanceof HTMLElement) update(target)
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
