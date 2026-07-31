import { ImageZoom } from 'fumadocs-ui/components/image-zoom';
import Image from 'next/image';
import type { ReactNode } from 'react';

interface Capability {
  title: string;
  description: string;
}

interface FlowStep {
  title: string;
  detail: string;
}

export function ConceptMap({
  label,
  steps,
}: {
  label: string;
  steps: FlowStep[];
}) {
  return (
    <figure className="not-prose my-6">
      <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
        {steps.map((step, index) => (
          <div className="relative min-w-0" key={step.title}>
            <div className="h-full rounded-lg border border-fd-border bg-fd-card px-4 py-3">
              <span className="inline-flex size-6 items-center justify-center rounded-full bg-fd-primary/10 text-xs font-semibold text-fd-primary">
                {index + 1}
              </span>
              <p className="mt-3 font-medium leading-5 text-fd-foreground">
                {step.title}
              </p>
              <p className="mt-1 text-xs leading-5 text-fd-muted-foreground">
                {step.detail}
              </p>
            </div>
            {index < steps.length - 1 ? (
              <span
                aria-hidden="true"
                className="absolute -right-2 top-1/2 z-10 hidden -translate-y-1/2 rounded-full border border-fd-border bg-fd-background px-1 text-xs text-fd-muted-foreground lg:block"
              >
                →
              </span>
            ) : null}
          </div>
        ))}
      </div>
      <figcaption className="mt-3 text-sm leading-6 text-fd-muted-foreground">
        {label}
      </figcaption>
    </figure>
  );
}

export function ArchitectureDiagram({
  alt,
  description,
  height,
  src,
  title,
  width,
}: {
  alt: string;
  description: string;
  height: number;
  src: string;
  title: string;
  width: number;
}) {
  return (
    <figure className="not-prose my-6 overflow-hidden rounded-xl border border-fd-border bg-fd-card">
      <ImageZoom alt={alt} height={height} src={src} width={width}>
        <Image
          alt={alt}
          className="block h-auto w-full"
          height={height}
          loading="eager"
          src={src}
          unoptimized
          width={width}
        />
      </ImageZoom>
      <figcaption className="border-t border-fd-border px-5 py-4">
        <span className="block font-medium text-fd-foreground">{title}</span>
        <span className="mt-1 block text-sm leading-6 text-fd-muted-foreground">
          {description}
        </span>
      </figcaption>
    </figure>
  );
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
