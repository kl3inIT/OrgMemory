import { useEffect, type ReactNode } from 'react'
import { useAuth } from 'react-oidc-context'
import { Button } from '@/components/ui/button'
import { AUTH_ENABLED } from '@/lib/auth'

export function AuthGate({ children }: { children: ReactNode }) {
  if (!AUTH_ENABLED) {
    return children
  }
  return <OidcGate>{children}</OidcGate>
}

function OidcGate({ children }: { children: ReactNode }) {
  const auth = useAuth()

  useEffect(() => {
    if (!auth.isLoading && !auth.isAuthenticated && !auth.error && !auth.activeNavigator) {
      void auth.signinRedirect()
    }
  }, [auth])

  if (auth.error) {
    return (
      <div className="flex min-h-svh flex-col items-center justify-center gap-4">
        <div className="text-sm text-muted-foreground">Sign-in failed: {auth.error.message}</div>
        <Button onClick={() => void auth.signinRedirect()}>Try again</Button>
      </div>
    )
  }

  if (!auth.isAuthenticated) {
    return (
      <div className="flex min-h-svh items-center justify-center text-sm text-muted-foreground">
        Redirecting to sign in...
      </div>
    )
  }

  return children
}
