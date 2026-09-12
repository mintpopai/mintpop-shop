import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ImageUploadButton from './ImageUploadButton.vue'
import { uploadAdminImage } from '../api-admin'
import { toast } from '../toast'

vi.mock('../api-admin', () => ({ uploadAdminImage: vi.fn() }))

const uploadMock = vi.mocked(uploadAdminImage)
let wrapper: VueWrapper | null = null

function render(props: { label?: string } = {}) {
  wrapper = mount(ImageUploadButton, { props })
  return wrapper
}

/** 往隐藏的 file input 里「选」一个文件并触发 change */
async function pick(w: VueWrapper, file: File) {
  const input = w.find('input[type="file"]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
  await flushPromises()
}

function png(size = 10): File {
  return new File([new Uint8Array(size)], 'a.png', { type: 'image/png' })
}

beforeEach(() => {
  toast.value = null
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

describe('ImageUploadButton', () => {
  it('默认文案「上传图片」，可由 label 覆盖；只收四种图片类型', () => {
    expect(render().find('button').text()).toBe('上传图片')
    wrapper?.unmount()
    expect(render({ label: '从本地上传' }).find('button').text()).toBe('从本地上传')
    expect(wrapper?.find('input[type="file"]').attributes('accept')).toBe(
      'image/jpeg,image/png,image/webp,image/gif',
    )
    expect(wrapper?.find('input[type="file"]').attributes('tabindex')).toBe('-1')
  })

  it('点按钮等于点隐藏的文件框', async () => {
    const w = render()
    const clickSpy = vi.spyOn(w.find('input[type="file"]').element as HTMLInputElement, 'click')
    await w.find('button').trigger('click')
    expect(clickSpy).toHaveBeenCalledOnce()
  })

  it('选中文件后上传，成功抛出 uploaded 事件带公开 URL', async () => {
    uploadMock.mockResolvedValue('https://shop-assets.mintpop.ai/products/2026/09/a.png')
    const w = render()
    const file = png()

    await pick(w, file)

    expect(uploadMock).toHaveBeenCalledWith(file)
    expect(w.emitted('uploaded')).toEqual([
      ['https://shop-assets.mintpop.ai/products/2026/09/a.png'],
    ])
  })

  it('上传中按钮禁用、文案变「上传中…」，结束后恢复', async () => {
    let resolve: (url: string) => void = () => {}
    uploadMock.mockReturnValue(new Promise<string>((r) => (resolve = r)))
    const w = render()

    await pick(w, png())
    expect(w.find('button').attributes('disabled')).toBeDefined()
    expect(w.find('button').text()).toBe('上传中…')

    resolve('https://x/a.png')
    await flushPromises()
    expect(w.find('button').attributes('disabled')).toBeUndefined()
    expect(w.find('button').text()).toBe('上传图片')
  })

  it('超过 5MB 本地直接拦下：提示、不发请求', async () => {
    const w = render()
    await pick(w, png(5 * 1024 * 1024 + 1))

    expect(uploadMock).not.toHaveBeenCalled()
    expect(toast.value).toEqual({ type: 'error', text: '图片不能超过 5 MB' })
    expect(w.emitted('uploaded')).toBeUndefined()
  })

  it('上传失败：toast 后端给的原因，不抛 uploaded', async () => {
    uploadMock.mockRejectedValue(new Error('只支持 JPEG、PNG、WebP、GIF 图片'))
    const w = render()
    await pick(w, png())

    expect(toast.value).toEqual({ type: 'error', text: '只支持 JPEG、PNG、WebP、GIF 图片' })
    expect(w.emitted('uploaded')).toBeUndefined()
  })

  it('选完后清空文件框的值，同一个文件再选一次也能触发', async () => {
    uploadMock.mockResolvedValue('https://x/a.png')
    const w = render()
    await pick(w, png())
    expect((w.find('input[type="file"]').element as HTMLInputElement).value).toBe('')
  })
})
