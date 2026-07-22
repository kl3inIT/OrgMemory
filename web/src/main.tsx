import { QueryClientProvider, QueryErrorResetBoundary } from "@tanstack/react-query"
import { RouterProvider } from "@tanstack/react-router"
import { lazy, StrictMode, Suspense } from "react"
import { createRoot } from "react-dom/client"
import { ErrorBoundary, type FallbackProps } from "react-error-boundary"
import { ThemeProvider } from "next-themes"

import "./lib/api-client"
import "./index.css"
import { AppToaster } from "@/components/app-toaster"
import { ApplicationError } from "@/components/states/application-error"
import { queryClient } from "@/lib/query-client"
import { router } from "@/router"

const QueryDevtools = lazy(async () => {
  if (!import.meta.env.DEV) return { default: () => null }
  const module = await import("@tanstack/react-query-devtools")
  return { default: module.ReactQueryDevtools }
})

function ApplicationCrash({ error, resetErrorBoundary }: FallbackProps) {
  return (
    <ApplicationError
      title="OrgMemory stopped unexpectedly"
      description="The application could not recover automatically. Your data was not changed."
      error={error}
      onRetry={resetErrorBoundary}
    />
  )
}

const rootElement = document.getElementById("root")

if (!rootElement) {
  throw new Error("OrgMemory root element was not found.")
}

createRoot(rootElement).render(
  <StrictMode>
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
      <QueryClientProvider client={queryClient}>
        <QueryErrorResetBoundary>
          {({ reset }) => (
            <ErrorBoundary FallbackComponent={ApplicationCrash} onReset={reset}>
              <RouterProvider router={router} context={{ queryClient }} />
            </ErrorBoundary>
          )}
        </QueryErrorResetBoundary>
        <AppToaster />
        <Suspense fallback={null}>
          <QueryDevtools initialIsOpen={false} />
        </Suspense>
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
)
