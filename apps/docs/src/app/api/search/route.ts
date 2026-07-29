import { source } from '@/lib/source';
import { createFromSource } from 'fumadocs-core/search/server';

export const { GET } = createFromSource(source, {
  // Orama has no Vietnamese stemmer. Keep locale-separated indexes while using
  // its English tokenizer for both until a reviewed Vietnamese tokenizer is selected.
  localeMap: {
    en: 'english',
    vi: 'english',
  },
});
