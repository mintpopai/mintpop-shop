import { locale } from './i18n'

/** 后端 UTC 时刻（ISO-8601 带 Z）按浏览器时区、当前语言渲染到分钟（全站统一格式） */
const formatter = new Intl.DateTimeFormat(locale, {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

export function formatDateTime(iso: string): string {
  return formatter.format(new Date(iso))
}
