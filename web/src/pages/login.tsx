import { Navigate, useSearch } from "@tanstack/react-router"

import { LoginForm } from "@/components/login-form"
import { ModeToggle } from "@/components/mode-toggle"
import { ApplicationError } from "@/components/states/application-error"
import { PageLoading } from "@/components/states/page-loading"
import { beginBrowserLogin } from "@/features/session/browser-login"
import { useBrowserSession } from "@/features/session/use-browser-session"

export function LoginPage() {
  const session = useBrowserSession()
  const search = useSearch({ from: "/login" })
  const statusMessage = search.error
    ? "Sign-in failed. Try again or contact your administrator."
    : search.loggedOut
      ? "You are signed out."
      : undefined

  if (session.isPending) return <PageLoading label="Checking your session" />

  if (session.isError) {
    return (
      <ApplicationError
        title="Sign-in is temporarily unavailable"
        description="OrgMemory could not reach the identity service."
        error={session.error}
        onRetry={() => void session.refetch()}
      />
    )
  }

  if (session.data?.authenticated) {
    return <Navigate to="/" replace />
  }

  return (
    <main className="relative grid min-h-dvh place-items-center bg-muted/30 p-6">
      <div className="absolute right-4 top-4">
        <ModeToggle />
      </div>
      <section className="w-full max-w-md" aria-labelledby="login-heading">
        <h1 id="login-heading" className="mb-6 text-2xl font-semibold tracking-tight">
          Your organization&apos;s memory
        </h1>
        <LoginForm statusMessage={statusMessage} onContinue={() => beginBrowserLogin()} />
      </section>
    </main>
  )
}
