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

/** UTC 时:分。概览的「今日」口径由后端按 UTC 划分，这条时间轴上的一切都用 UTC，不混本地时区 */
export function formatUtcTime(iso: string): string {
  const d = new Date(iso)
  return `${String(d.getUTCHours()).padStart(2, '0')}:${String(d.getUTCMinutes()).padStart(2, '0')}`
}

/** UTC 的 YYYY-MM-DD HH:mm。概览整页都按 UTC 展示，与后端「今日」口径一致 */
export function formatUtcDateTime(iso: string): string {
  return `${utcDate(iso)} ${formatUtcTime(iso)}`
}

/** UTC 日期 YYYY-MM-DD，用于判定某条记录是否属于「今天（UTC）」 */
export function utcDate(iso: string | Date): string {
  return (typeof iso === 'string' ? new Date(iso) : iso).toISOString().slice(0, 10)
}

/** 该时刻在 UTC 一天中的位置，0（00:00）到 1（24:00） */
export function utcDayProgress(iso: string | Date): number {
  const d = typeof iso === 'string' ? new Date(iso) : iso
  return (d.getUTCHours() * 60 + d.getUTCMinutes()) / 1440
}
