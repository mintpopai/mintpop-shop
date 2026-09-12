import { describe, expect, it } from 'vitest'
import { accentColors } from './accent'

describe('accentColors', () => {
  it('五个已知 accent 各自返回独立配色（from/to/ink 三色齐全）', () => {
    const inks = ['MINT', 'VIOLET', 'SKY', 'AMBER', 'ROSE'].map((accent) => {
      const colors = accentColors(accent)
      expect(colors.from).toMatch(/^#[0-9a-f]{6}$/)
      expect(colors.to).toMatch(/^#[0-9a-f]{6}$/)
      expect(colors.ink).toMatch(/^#[0-9a-f]{6}$/)
      return colors.ink
    })
    // 主题色互不相同，否则色表写重了
    expect(new Set(inks).size).toBe(5)
  })

  it('未知值与空串回退 MINT，不渲染成透明卡片', () => {
    expect(accentColors('NEON')).toEqual(accentColors('MINT'))
    expect(accentColors('')).toEqual(accentColors('MINT'))
  })

  it('大小写敏感：小写 mint 不算已知值，同样回退 MINT（后端约定全大写）', () => {
    expect(accentColors('mint')).toEqual(accentColors('MINT'))
    expect(accentColors('rose')).not.toEqual(accentColors('ROSE'))
  })
})
