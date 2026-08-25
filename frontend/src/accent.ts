/**
 * 卡片主题色：后端下发 accent 枚举，前端在商品卡与详情页共用同一张色表。
 * 两处各存一份必然漂移，统一收口在这里。
 */
export interface AccentColors {
  from: string
  to: string
  ink: string
}

const ACCENTS: Record<string, AccentColors> = {
  MINT: { from: '#d9f7ec', to: '#b7f0dd', ink: '#0fb389' },
  VIOLET: { from: '#e6e1fb', to: '#d3ccf7', ink: '#6d5bd0' },
  SKY: { from: '#ddebfa', to: '#c5ddf6', ink: '#2f7fd1' },
  AMBER: { from: '#faeeda', to: '#f6e2c0', ink: '#c07f1f' },
  ROSE: { from: '#fae3e7', to: '#f6ccd4', ink: '#d04a68' },
}

/** 取 accent 对应的配色，未知值回退 MINT（与后端约定一致，别渲染成透明卡片） */
export function accentColors(accent: string): AccentColors {
  return ACCENTS[accent] ?? ACCENTS.MINT
}
