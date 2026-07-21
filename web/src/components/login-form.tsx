import { ArrowRight, Building2 } from "lucide-react"
import type { ComponentProps } from "react"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { cn } from "@/lib/utils"

export function LoginForm({
  className,
  onContinue,
  statusMessage,
  ...props
}: ComponentProps<"div"> & { onContinue: () => void; statusMessage?: string }) {
  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <Card className="border-border/70 shadow-none">
        <CardHeader>
          <div className="mb-4 grid size-10 place-items-center rounded-md border bg-muted">
            <Building2 className="size-5" aria-hidden="true" />
          </div>
          <CardTitle>Sign in</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {statusMessage ? (
            <p className="rounded-md border bg-muted px-3 py-2 text-sm" role="status">
              {statusMessage}
            </p>
          ) : null}
          <Button className="w-full justify-between" size="lg" onClick={onContinue}>
            Continue with company account
            <ArrowRight className="size-4" aria-hidden="true" />
          </Button>
          <p className="text-xs leading-relaxed text-muted-foreground">
            Authentication and MFA are managed by your identity provider.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
