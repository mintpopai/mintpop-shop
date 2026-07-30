/** 后端 UTC 时刻（ISO-8601 带 Z）按浏览器时区渲染到分钟；管理端固定中文，locale 恒为 zh-CN */
const formatter = new Intl.DateTimeFormat('zh-CN', {
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
