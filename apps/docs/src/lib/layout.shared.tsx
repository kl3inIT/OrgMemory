import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';
import { appName } from './shared';
import { docsHome, type DocsLanguage } from './i18n';

export function baseOptions(language: DocsLanguage): BaseLayoutProps {
  return {
    nav: {
      title: appName,
      url: docsHome(language),
    },
  };
}
