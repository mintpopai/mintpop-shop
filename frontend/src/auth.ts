import { ref } from 'vue'
import { fetchMe, updateMyLocale, type Me } from './api'
import { locale, storeLocale, type AppLocale } from './i18n'

/** 当前登录用户；null = 游客 */
export const currentUser = ref<Me | null>(null)

/**
 * 启动时拉取登录态：任何失败（含 401）都按游客处理，不阻塞页面。
 * 顺带对齐语言偏好——服务端存了就以服务端为准（换设备也一致），没存就把本地偏好补上去。
 */
export async function loadCurrentUser(): Promise<void> {
  try {
    currentUser.value = await fetchMe()
  } catch {
    currentUser.value = null
    return
  }
  syncLocale(currentUser.value.locale)
}

function syncLocale(serverLocale: string | null): void {
  if (serverLocale !== 'zh-CN' && serverLocale !== 'en-US') {
    // 服务端没有偏好：把本次会话语言补写过去（失败静默，不影响页面）
    void updateMyLocale(locale).catch(() => undefined)
    return
  }
  if (serverLocale !== locale) {
    // 服务端偏好优先：写回本地后整页刷新一次；刷新后两者一致，不会再触发
    storeLocale(serverLocale as AppLocale)
    location.reload()
  }
}

/**
 * 整页跳转后端登录入口（OIDC 授权码流程）。
 * 带上当前路径（仅 path + query，不含 hash/origin），登录成功后由后端原样回跳，
 * 避免发货邮件深链等场景登录完丢失原本要看的页面。
 */
export function gotoLogin(): void {
  const redirect = encodeURIComponent(location.pathname + location.search)
  window.location.href = `/auth/login?redirect=${redirect}`
}

/** 整页跳转登出（清会话 + 账号中心单点登出） */
export function gotoLogout(): void {
  window.location.href = '/auth/logout'
}
