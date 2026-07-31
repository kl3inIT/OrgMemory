import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';
import { HistoryIcon } from 'lucide-react';
import { appName } from './shared';
import { docsHome, type DocsLanguage, withLocale } from './i18n';

export function baseOptions(language: DocsLanguage): BaseLayoutProps {
  return {
    nav: {
      title: appName,
      url: docsHome(language),
    },
    links: [
      {
        text: language === 'vi' ? 'Nhật ký thay đổi' : 'Changelog',
        url: withLocale('/docs/changelog', language),
        icon: <HistoryIcon />,
        active: 'url',
      },
    ],
  };
}
