import { useEffect, type ReactNode } from "react"

import { ApplicationError } from "@/components/states/application-error"
import { PageLoading } from "@/components/states/page-loading"
import { beginBrowserLogin, currentReturnPath } from "@/features/session/browser-login"
import { useBrowserSession } from "@/features/session/use-browser-session"

function AuthenticationRedirect() {
  useEffect(() => {
    beginBrowserLogin(currentReturnPath())
  }, [])

  return (
    <PageLoading label="Redirecting to company sign-in" />
  )
}

export function AuthGate({ children }: { children: ReactNode }) {
  const session = useBrowserSession()

  if (session.isPending) {
    return <PageLoading />
  }

  if (session.isError) {
    return (
      <ApplicationError
        title="Workspace access could not be verified"
        description="The identity service may be unavailable, or this account has not been provisioned in OrgMemory."
        error={session.error}
        onRetry={() => void session.refetch()}
      />
    )
  }

  if (!session.data?.authenticated) {
    return <AuthenticationRedirect />
  }

  return children
}
