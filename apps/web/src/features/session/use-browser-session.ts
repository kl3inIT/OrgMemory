import { useQuery } from "@tanstack/react-query"

import { browserSessionQueryOptions } from "@/features/session/session-query"

export function useBrowserSession() {
  return useQuery(browserSessionQueryOptions())
}
