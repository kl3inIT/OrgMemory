import { queryOptions } from "@tanstack/react-query"

import { getBrowserSessionOptions } from "@/lib/hey-api/@tanstack/react-query.gen"

export function browserSessionQueryOptions() {
  return queryOptions({
    ...getBrowserSessionOptions(),
    staleTime: 30_000,
    retry: false,
    meta: { silent: true },
  })
}
