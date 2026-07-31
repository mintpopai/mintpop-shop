import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN.json'
import enUS from './locales/en-US.json'

export type AppLocale = 'zh-CN' | 'en-US'

const STORAGE_KEY = 'locale'

/** 解析本次会话语言：localStorage 偏好 → 浏览器语言（en* 视为英文）→ 回退中文 */
function resolveLocale(): AppLocale {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === 'zh-CN' || saved === 'en-US') {
    return saved
  }
  return navigator.language.toLowerCase().startsWith('en') ? 'en-US' : 'zh-CN'
}

/** 本次会话语言：切换走 setLocale 整页刷新，会话内不变 */
export const locale: AppLocale = resolveLocale()
document.documentElement.lang = locale

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale,
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN, 'en-US': enUS },
})

/** 组件外取文案（api.ts、视图脚本）；模板内用全局 $t */
export const t = i18n.global.t

/** 只写语言偏好，不刷新页面（供启动时采纳服务端偏好使用） */
export function storeLocale(next: AppLocale): void {
  localStorage.setItem(STORAGE_KEY, next)
}

/** 切换语言：写偏好后整页刷新，让界面与后端数据一起按新语言重取 */
export function setLocale(next: AppLocale): void {
  if (next === locale) {
    return
  }
  storeLocale(next)
  location.reload()
}
