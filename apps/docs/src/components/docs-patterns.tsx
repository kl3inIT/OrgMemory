import type { ReactNode } from 'react';

interface Capability {
  title: string;
  description: string;
}

export function CapabilityGrid({ items }: { items: Capability[] }) {
  return (
    <div className="not-prose my-6 grid gap-3 sm:grid-cols-2">
      {items.map((item) => (
        <article
          key={item.title}
          className="rounded-xl border border-fd-border bg-fd-card p-5 text-fd-card-foreground"
        >
          <h3 className="font-medium">{item.title}</h3>
          <p className="mt-2 text-sm leading-6 text-fd-muted-foreground">
            {item.description}
          </p>
        </article>
      ))}
    </div>
  );
}

export function DiagramFrame({
  children,
  description,
  title,
}: {
  children: ReactNode;
  description: string;
  title: string;
}) {
  return (
    <figure className="not-prose my-6 overflow-hidden rounded-xl border border-fd-border bg-fd-card">
      <div className="flex min-h-40 items-center justify-center bg-[radial-gradient(circle_at_top_left,var(--color-fd-accent),transparent_58%)] px-6 py-10 text-center font-mono text-sm text-fd-muted-foreground">
        {children}
      </div>
      <figcaption className="border-t border-fd-border px-5 py-4">
        <span className="block font-medium text-fd-foreground">{title}</span>
        <span className="mt-1 block text-sm leading-6 text-fd-muted-foreground">
          {description}
        </span>
      </figcaption>
    </figure>
  );
}

export function ApiExample({ code, title }: { code: string; title: string }) {
  return (
    <figure className="not-prose my-6 overflow-hidden rounded-xl border border-fd-border bg-fd-card">
      <figcaption className="border-b border-fd-border px-4 py-3 text-sm font-medium">
        {title}
      </figcaption>
      <pre className="overflow-x-auto p-4 text-sm leading-6">
        <code>{code}</code>
      </pre>
    </figure>
  );
}

export function VerificationBlock({
  children,
  title,
}: {
  children: ReactNode;
  title: string;
}) {
  return (
    <aside className="not-prose my-6 rounded-xl border border-emerald-500/30 bg-emerald-500/5 p-5">
      <p className="font-medium text-fd-foreground">{title}</p>
      <div className="mt-2 text-sm leading-6 text-fd-muted-foreground">
        {children}
      </div>
    </aside>
  );
}
