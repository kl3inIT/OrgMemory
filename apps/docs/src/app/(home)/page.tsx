import Link from 'next/link';
import {
  ArrowRight,
  Boxes,
  Braces,
  CheckCircle2,
  LockKeyhole,
  Network,
  ServerCog,
} from 'lucide-react';

export default function HomePage() {
  const includeDrafts = process.env.DOCS_INCLUDE_DRAFTS === 'true';
  const quickstartUrl = includeDrafts ? '/docs/overview/quickstart' : '/docs';
  const architectureUrl = includeDrafts
    ? '/docs/architecture-security/system-description'
    : '/docs';

  return (
    <div className="flex w-full flex-1 flex-col">
      <section className="relative isolate overflow-hidden border-b border-fd-border">
        <div className="absolute inset-0 -z-10 bg-[radial-gradient(circle_at_15%_20%,color-mix(in_oklab,var(--color-fd-primary)_15%,transparent),transparent_36%),radial-gradient(circle_at_85%_65%,color-mix(in_oklab,var(--color-fd-accent)_70%,transparent),transparent_42%)]" />
        <div className="mx-auto grid w-full max-w-7xl gap-12 px-6 py-20 lg:grid-cols-[minmax(0,1.1fr)_minmax(320px,0.9fr)] lg:items-center lg:py-28">
          <div>
            <p className="mb-5 text-sm font-medium tracking-[0.16em] text-fd-muted-foreground uppercase">
              Governed organizational memory
            </p>
            <h1 className="max-w-4xl text-4xl font-semibold tracking-tight text-balance md:text-6xl">
              Secure context for people and AI, grounded in the permissions you
              already trust.
            </h1>
            <p className="mt-6 max-w-2xl text-lg leading-8 text-fd-muted-foreground">
              Learn how OrgMemory ingests organizational knowledge, preserves
              access evidence, builds permission-aware context, and delivers it
              through the product, API, Assistant, and MCP.
            </p>
            <div className="mt-9 flex flex-wrap gap-3">
              <Link
                href={quickstartUrl}
                className="inline-flex items-center gap-2 rounded-lg bg-fd-primary px-5 py-3 text-sm font-medium text-fd-primary-foreground"
              >
                Start the quickstart
                <ArrowRight aria-hidden="true" className="size-4" />
              </Link>
              <Link
                href="https://om.kl3in.tech"
                className="rounded-lg border border-fd-border bg-fd-background/70 px-5 py-3 text-sm font-medium backdrop-blur"
              >
                Open the product
              </Link>
            </div>
          </div>
          <div
            aria-label="OrgMemory governance flow"
            className="rounded-3xl border border-fd-border bg-fd-card/80 p-5 shadow-2xl shadow-fd-primary/5 backdrop-blur"
          >
            <div className="grid gap-3">
              {[
                [Network, 'Connect', 'Discover knowledge and source permissions.'],
                [LockKeyhole, 'Govern', 'Preserve identity and access evidence.'],
                [Boxes, 'Understand', 'Build searchable graph and vector context.'],
                [Braces, 'Deliver', 'Serve verified context through supported clients.'],
              ].map(([Icon, title, description], index) => (
                <div
                  key={String(title)}
                  className="flex items-start gap-4 rounded-2xl border border-fd-border bg-fd-background/75 p-4"
                >
                  <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-fd-primary/10 text-fd-primary">
                    <Icon aria-hidden="true" className="size-5" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-medium text-fd-muted-foreground">
                        0{index + 1}
                      </span>
                      <h2 className="font-medium">{String(title)}</h2>
                    </div>
                    <p className="mt-1 text-sm leading-6 text-fd-muted-foreground">
                      {String(description)}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>
      <section className="mx-auto w-full max-w-7xl px-6 py-16">
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
          <div>
            <p className="text-sm font-medium text-fd-primary">Choose your path</p>
            <h2 className="mt-2 text-3xl font-semibold tracking-tight">
              Reach the right evidence without reading the repository.
            </h2>
          </div>
          <Link
            href={architectureUrl}
            className="inline-flex items-center gap-2 text-sm font-medium text-fd-primary"
          >
            Explore system architecture
            <ArrowRight aria-hidden="true" className="size-4" />
          </Link>
        </div>
        <div
          aria-label="Documentation audiences"
          className="mt-8 grid gap-4 md:grid-cols-2 lg:grid-cols-4"
        >
          {[
            [CheckCircle2, 'Evaluate', 'Review scope, security posture, coverage, and limitations.'],
            [ServerCog, 'Operate', 'Deploy, configure, back up, and troubleshoot safely.'],
            [Braces, 'Integrate', 'Use supported API, MCP, and connector contracts.'],
            [LockKeyhole, 'Administer', 'Manage identity, sources, permissions, and audit evidence.'],
          ].map(([Icon, title, description]) => (
            <article
              key={String(title)}
              className="rounded-2xl border border-fd-border bg-fd-card p-5"
            >
              <Icon aria-hidden="true" className="size-5 text-fd-primary" />
              <h3 className="mt-5 font-medium">{String(title)}</h3>
              <p className="mt-2 text-sm leading-6 text-fd-muted-foreground">
                {String(description)}
              </p>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
