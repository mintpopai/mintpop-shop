/** 后端统一返回结构：code=0 成功，非 0 失败取 msg */
export interface ApiResponse<T> {
  code: number
  data: T | null
  msg: string | null
}

/** 当前用户（镜像后端 MeResponse） */
export interface Me {
  id: number
  email: string
  nickname: string | null
  avatarUrl: string | null
  /** 是否管理员；这里只决定渲染管理端还是无权限页，安全边界在后端 AdminInterceptor */
  admin: boolean
}

/** 未登录/会话过期（HTTP 401），调用方据此引导登录 */
export class UnauthorizedError extends Error {
  constructor() {
    super('登录已过期，请重新登录')
  }
}

/**
 * 统一请求封装：401 转类型化错误、业务码判定、错误文案取后端 msg。
 * 管理端不做双语，Accept-Language 恒为 zh-CN——后端的状态文案与错误信息随之固定中文，
 * 与页面写死的中文文案保持一致。
 */
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(path, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        'Accept-Language': 'zh-CN',
        ...init?.headers,
      },
    })
  } catch {
    // 网络失败：不把原始英文报错透给用户
    throw new Error('网络异常，请稍后重试')
  }
  // 鉴权中间件的 401 是唯一非 200 业务入口，转成类型化错误
  if (res.status === 401) {
    throw new UnauthorizedError()
  }
  let body: ApiResponse<T>
  try {
    body = (await res.json()) as ApiResponse<T>
  } catch {
    throw new Error('网络异常，请稍后重试')
  }
  if (body.code !== 0) {
    throw new Error(body.msg ?? '请求失败')
  }
  return body.data as T
}

/** 当前登录用户（401 抛 UnauthorizedError 表示未登录） */
export function fetchMe(): Promise<Me> {
  return request<Me>('/api/me')
}

/** 美分转美元展示 */
export function formatPrice(cents: number): string {
  return `$${(cents / 100).toFixed(2)}`
}
