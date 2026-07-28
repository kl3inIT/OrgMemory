import {
  type FileObject,
  printErrors,
  scanURLs,
  validateFiles,
} from 'next-validate-link';
import { register } from 'fumadocs-mdx/node';

process.env.DOCS_INCLUDE_DRAFTS = 'true';
register();

async function checkLinks() {
  const { source } = await import('../src/lib/source');
  const scanned = await scanURLs({
    preset: 'next',
    populate: {
      'docs/[[...slug]]': source.getPages().map((page) => ({
        value: {
          slug: page.slugs,
        },
        hashes: page.data.toc.map((item) => item.url.slice(1)),
      })),
    },
  });

  const files = await Promise.all(
    source.getPages().map(async (page): Promise<FileObject> => ({
      path: page.absolutePath ?? page.path,
      content: await page.data.getText('raw'),
      url: page.url,
      data: page.data,
    })),
  );

  printErrors(
    await validateFiles(files, {
      scanned,
      checkRelativePaths: 'as-url',
      markdown: {
        components: {
          Card: { attributes: ['href'] },
        },
      },
    }),
    true,
  );
}

void checkLinks().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
