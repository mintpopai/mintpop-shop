import { ref } from 'vue'
import { fetchMe, type Me } from './api'

/** 当前登录用户；null = 游客 */
export const currentUser = ref<Me | null>(null)

/** 启动时拉取登录态：任何失败（含 401）都按游客处理，不阻塞页面 */
export async function loadCurrentUser(): Promise<void> {
  try {
    currentUser.value = await fetchMe()
  } catch {
    currentUser.value = null
  }
}

/** 整页跳转后端登录入口（OIDC 授权码流程） */
export function gotoLogin(): void {
  window.location.href = '/auth/login'
}

/** 整页跳转登出（清会话 + 账号中心单点登出） */
export function gotoLogout(): void {
  window.location.href = '/auth/logout'
}
