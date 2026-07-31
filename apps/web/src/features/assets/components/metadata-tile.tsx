export function MetadataTile({
  label,
  value,
  mono = false,
}: {
  label: string
  value?: string
  mono?: boolean
}) {
  return (
    <div className="bg-surface-subtle px-5 py-3">
      <p className="text-metadata text-content-muted">{label}</p>
      <p
        className={`mt-1 truncate text-supporting text-content-primary ${mono ? "font-mono" : ""}`}
      >
        {value ?? "—"}
      </p>
    </div>
  )
}
