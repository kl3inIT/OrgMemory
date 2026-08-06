import { useEffect, useState } from "react"

export function useDebouncedValue<Value>(value: Value, delay = 300): Value {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay)
    return () => window.clearTimeout(timer)
  }, [delay, value])

  return debounced
}
