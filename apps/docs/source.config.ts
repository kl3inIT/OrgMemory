import { defineConfig, defineDocs } from 'fumadocs-mdx/config';
import { metaSchema, pageSchema } from 'fumadocs-core/source/schema';
import { z } from 'zod';

const audience = z
  .array(z.enum(['adopter', 'user', 'administrator', 'developer', 'evaluator']))
  .min(1);

const publicPageSchema = pageSchema.extend({
  audience,
  status: z.enum(['public', 'draft']),
  sourceRefs: z.array(z.string().min(1)).min(1),
  lastReviewed: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
});

export const docs = defineDocs({
  dir: 'content/docs',
  docs: {
    schema: publicPageSchema,
    postprocess: {
      includeProcessedMarkdown: true,
    },
  },
  meta: {
    schema: metaSchema,
  },
});

export default defineConfig({
  mdxOptions: {},
});
