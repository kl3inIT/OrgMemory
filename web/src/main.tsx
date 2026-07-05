import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { RouterProvider } from '@tanstack/react-router'
import { ThemeProvider } from 'next-themes'
import { Toaster, toast } from 'sonner'

import { router } from './router'
import './index.css'
import '@xyflow/react/dist/style.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
  queryCache: new QueryCache({
    onError: (_error, query) => {
      if (query.meta?.silent) return
      toast.error('Could not load OrgMemory data.', { id: `query-${query.queryHash}` })
    },
  }),
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
        <Toaster position="bottom-right" richColors />
        <ReactQueryDevtools initialIsOpen={false} />
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
)
