import { defineI18n } from 'fumadocs-core/i18n';
import { uiTranslations } from 'fumadocs-ui/i18n';

export const i18n = defineI18n({
  languages: ['en', 'vi'],
  defaultLanguage: 'en',
  hideLocale: 'default-locale',
  parser: 'dot',
  fallbackLanguage: 'en',
});

export type DocsLanguage = (typeof i18n.languages)[number];

export function isDocsLanguage(value: string): value is DocsLanguage {
  return i18n.languages.some((language) => language === value);
}

export function withLocale(pathname: string, language: DocsLanguage): string {
  return language === i18n.defaultLanguage ? pathname : `/${language}${pathname}`;
}

export function docsHome(language: DocsLanguage): string {
  return withLocale('/docs/overview', language);
}

export const translations = i18n
  .translations()
  .extend(uiTranslations())
  .add({
    en: {
      displayName: 'English',
    },
    vi: {
      displayName: 'Tiếng Việt',
      'Ask AI(AI chat button)': 'Hỏi AI',
      'Back to Home(404 not found page)': 'Về trang tài liệu',
      'Choose a language(language switcher)': 'Chọn ngôn ngữ',
      'Choose a language(language switcher)(aria-label)': 'Chọn ngôn ngữ',
      'Close Search(search dialog)(aria-label)': 'Đóng tìm kiếm',
      'Close Sidebar(aria-label)': 'Đóng thanh bên',
      'Close Sidebar(sidebar)(aria-label)': 'Đóng thanh bên',
      'Collapse Sidebar(sidebar)(aria-label)': 'Thu gọn thanh bên',
      'Copy Anchor Link(heading anchor)(aria-label)': 'Sao chép liên kết mục',
      'Copy Markdown(page actions)': 'Sao chép Markdown',
      'Copy Text(code block)(aria-label)': 'Sao chép nội dung',
      'Copied Text(code block)(aria-label)': 'Đã sao chép',
      'Dark(theme switcher)(aria-label)': 'Giao diện tối',
      'Hide Sidebar(sidebar)': 'Ẩn thanh bên',
      'Last updated on(page footer)': 'Cập nhật lần cuối',
      'Layout Tab(layout tab trigger)': 'Chọn bộ tài liệu',
      'Light(theme switcher)(aria-label)': 'Giao diện sáng',
      'Next Page(pagination)': 'Trang tiếp',
      'No Headings(table of contents)': 'Trang này không có đề mục',
      'No results found(search dialog)': 'Không tìm thấy kết quả',
      'On this page(table of contents)': 'Trong trang này',
      'Open Search(search trigger)(aria-label)': 'Mở tìm kiếm',
      'Open Sidebar(aria-label)': 'Mở thanh bên',
      'Open Sidebar(sidebar)(aria-label)': 'Mở thanh bên',
      'Open(page actions)': 'Mở',
      'Page Not Found(404 not found page)': 'Không tìm thấy trang',
      'Previous Page(pagination)': 'Trang trước',
      'Search(search dialog)': 'Tìm kiếm',
      'Search(search trigger)': 'Tìm kiếm',
      'Show Sidebar(sidebar)': 'Hiện thanh bên',
      'System(theme switcher)(aria-label)': 'Theo hệ thống',
      'Table of Contents(inline table of contents)': 'Mục lục',
      'The page you are looking for might have been removed, had its name changed, or is temporarily unavailable.(404 not found page)':
        'Trang bạn tìm có thể đã bị xóa, đổi tên hoặc tạm thời không khả dụng.',
      'Toggle Theme(theme switcher)(aria-label)': 'Đổi giao diện',
      'View as Markdown(page actions)': 'Xem dạng Markdown',
    },
  });
