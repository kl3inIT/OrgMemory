import { QueryCache, QueryClient } from "@tanstack/react-query"
import { toast } from "sonner"

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
  queryCache: new QueryCache({
    onError: (_error, query) => {
      if (query.meta?.silent || query.state.data === undefined) return
      toast.error("Could not refresh OrgMemory data.", { id: `query-${query.queryHash}` })
    },
  }),
})
