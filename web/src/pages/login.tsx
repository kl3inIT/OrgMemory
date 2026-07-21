import { Navigate } from "@tanstack/react-router"
import { LoginForm } from "@/components/login-form"
import { ModeToggle } from "@/components/mode-toggle"
import { beginBrowserLogin } from "@/features/session/browser-login"
import { useBrowserSession } from "@/features/session/use-browser-session"

export function LoginPage() {
  const session = useBrowserSession()
  const search = new URLSearchParams(window.location.search)
  const statusMessage = search.has("error")
    ? "Sign-in failed. Try again or contact your administrator."
    : search.has("loggedOut")
      ? "You are signed out."
      : undefined

  if (session.data?.authenticated) {
    return <Navigate to="/" replace />
  }

  return (
    <main className="relative grid min-h-svh place-items-center bg-muted/30 p-6">
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
