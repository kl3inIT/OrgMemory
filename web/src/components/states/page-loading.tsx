import { Skeleton } from "@/components/ui/skeleton"

export function PageLoading({ label = "Loading workspace" }: { label?: string }) {
  return (
    <main className="grid min-h-dvh place-items-center p-6" role="status" aria-live="polite">
      <div className="w-full max-w-md space-y-4" aria-hidden="true">
        <Skeleton className="h-7 w-36" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-10 w-full" />
      </div>
      <span className="sr-only">{label}</span>
    </main>
  )
}
