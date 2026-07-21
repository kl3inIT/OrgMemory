import { Loader2, ShieldAlert } from "lucide-react"
import { useEffect, type ReactNode } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { beginBrowserLogin, currentReturnPath } from "@/features/session/browser-login"
import { useBrowserSession } from "@/features/session/use-browser-session"

function AuthenticationRedirect() {
  useEffect(() => {
    beginBrowserLogin(currentReturnPath())
  }, [])

  return (
    <div className="grid min-h-svh place-items-center p-6" aria-label="Redirecting to company sign-in">
      <Loader2 className="size-5 animate-spin text-muted-foreground" />
    </div>
  )
}

export function AuthGate({ children }: { children: ReactNode }) {
  const session = useBrowserSession()

  if (session.isPending) {
    return (
      <div className="grid min-h-svh place-items-center p-6" aria-label="Loading your workspace">
        <div className="w-full max-w-sm space-y-3">
          <Skeleton className="h-8 w-40" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      </div>
    )
  }

  if (session.isError) {
    return (
      <div className="grid min-h-svh place-items-center p-6">
        <Card className="w-full max-w-md">
          <CardHeader>
            <ShieldAlert className="mb-2 size-5 text-destructive" />
            <CardTitle>Workspace access could not be verified</CardTitle>
            <CardDescription>
              The identity service may be unavailable, or this account has not been provisioned in OrgMemory.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button onClick={() => void session.refetch()}>Try again</Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  if (!session.data?.authenticated) {
    return <AuthenticationRedirect />
  }

  return children
}
