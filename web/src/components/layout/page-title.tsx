export function PageTitle({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div className="space-y-1">
      <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">{title}</h1>
      <p className="text-sm text-muted-foreground md:text-base">{subtitle}</p>
    </div>
  )
}
