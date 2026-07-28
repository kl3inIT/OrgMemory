import { Skeleton } from "@/components/ui/skeleton"
import { cn } from "@/lib/utils"

export function LoadingState({
  label = "Loading workspace",
  className,
}: {
  label?: string
  className?: string
}) {
  return (
    <section className={cn("grid place-items-center p-6", className)} role="status" aria-live="polite">
      <div className="w-full max-w-md space-y-4" aria-hidden="true">
        <Skeleton className="h-7 w-36" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-10 w-full" />
      </div>
      <span className="sr-only">{label}</span>
    </section>
  )
}

export function PageLoading({ label = "Loading workspace" }: { label?: string }) {
  return (
    <main className="min-h-dvh">
      <LoadingState className="min-h-dvh" label={label} />
    </main>
  )
}
