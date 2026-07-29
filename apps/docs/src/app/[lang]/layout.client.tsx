'use client';

import { getDocsCategory } from '@/lib/docs-category';
import { useParams } from 'next/navigation';
import type { ReactNode } from 'react';

export function Body({ children }: { children: ReactNode }) {
  const params = useParams<{ slug?: string[] }>();
  const category = getDocsCategory(params.slug);

  return <body className={`${category} flex min-h-screen flex-col`}>{children}</body>;
}
