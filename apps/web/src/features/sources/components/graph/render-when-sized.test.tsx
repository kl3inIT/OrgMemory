import { act, render, screen } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { RenderWhenSized } from "@/features/sources/components/graph/render-when-sized"

class ResizeObserverHarness implements ResizeObserver {
  static instance: ResizeObserverHarness | null = null

  readonly observe = vi.fn((target: Element) => {
    this.target = target
  })
  readonly unobserve = vi.fn()
  readonly disconnect = vi.fn()
  private target: Element | null = null

  constructor(private readonly callback: ResizeObserverCallback) {
    ResizeObserverHarness.instance = this
  }

  emit(width: number, height: number) {
    if (!(this.target instanceof HTMLElement)) return
    Object.defineProperties(this.target, {
      offsetWidth: { configurable: true, value: Math.trunc(width) },
      offsetHeight: { configurable: true, value: Math.trunc(height) },
    })
    this.callback(
      [
        {
          target: this.target,
          contentRect: { width, height },
        } as unknown as ResizeObserverEntry,
      ],
      this,
    )
  }
}

describe("RenderWhenSized", () => {
  beforeEach(() => {
    ResizeObserverHarness.instance = null
    vi.stubGlobal("ResizeObserver", ResizeObserverHarness)
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockReturnValue({
      width: 0,
      height: 0,
    } as DOMRect)
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it("mounts its child only while both container dimensions are positive", () => {
    render(
      <RenderWhenSized className="h-full w-full">
        <div>sigma renderer</div>
      </RenderWhenSized>,
    )

    expect(screen.queryByText("sigma renderer")).not.toBeInTheDocument()

    act(() => ResizeObserverHarness.instance?.emit(0.5, 0.5))
    expect(screen.queryByText("sigma renderer")).not.toBeInTheDocument()

    act(() => ResizeObserverHarness.instance?.emit(640, 480))
    expect(screen.getByText("sigma renderer")).toBeInTheDocument()

    act(() => ResizeObserverHarness.instance?.emit(640, 0))
    expect(screen.queryByText("sigma renderer")).not.toBeInTheDocument()
  })
})
