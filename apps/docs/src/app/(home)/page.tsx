import Link from 'next/link';

export default function HomePage() {
  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col justify-center px-6 py-20 md:py-28">
      <p className="mb-5 text-sm font-medium tracking-[0.16em] text-fd-muted-foreground uppercase">
        Governed organizational memory
      </p>
      <h1 className="max-w-4xl text-4xl font-semibold tracking-tight text-balance md:text-6xl">
        Understand how OrgMemory turns enterprise knowledge into secure,
        permission-aware AI context.
      </h1>
      <p className="mt-6 max-w-2xl text-lg leading-8 text-fd-muted-foreground">
        This independent portal will cover product concepts, self-hosting,
        administration, integrations, architecture, security, and verification.
      </p>
      <div className="mt-9 flex flex-wrap gap-3">
        <Link
          href="/docs"
          className="rounded-lg bg-fd-primary px-5 py-3 text-sm font-medium text-fd-primary-foreground"
        >
          Read the foundation
        </Link>
        <Link
          href="https://github.com/kl3inIT/OrgMemory"
          className="rounded-lg border border-fd-border px-5 py-3 text-sm font-medium"
        >
          View source
        </Link>
      </div>
      <section
        aria-label="Documentation audiences"
        className="mt-14 grid gap-4 md:grid-cols-2 lg:grid-cols-4"
      >
        {[
          ['Adopt', 'Evaluate the product thesis and security posture.'],
          ['Operate', 'Deploy, configure, back up, and troubleshoot.'],
          ['Integrate', 'Use the API, MCP, and connector contracts.'],
          ['Verify', 'Trace requirements to architecture and tests.'],
        ].map(([title, description]) => (
          <article key={title} className="rounded-xl border border-fd-border p-5">
            <h2 className="font-medium">{title}</h2>
            <p className="mt-2 text-sm leading-6 text-fd-muted-foreground">
              {description}
            </p>
          </article>
        ))}
      </section>
    </main>
  );
}
