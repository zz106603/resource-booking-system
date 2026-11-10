import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import SpaceListView from '../views/SpaceListView.vue'
import { listSpaces } from '../api/spaces'

vi.mock('../api/spaces', () => ({ listSpaces: vi.fn() }))
const list = vi.mocked(listSpaces)
const space = { id: 1, name: '회의실 A', description: '창문이 있는 회의실', capacity: 6, createdAt: '2026-01-01T00:00:00Z' }
const page = { content: [space], page: 0, size: 20, totalElements: 21, totalPages: 2 }

describe('공간 목록 화면', () => {
  afterEach(() => vi.resetAllMocks())

  it('응답을_기다리는_동안_로딩을_표시한다', () => {
    list.mockReturnValue(new Promise(() => {}))
    const wrapper = mount(SpaceListView, { global: { stubs: { RouterLink: RouterLinkStub } } })
    expect(wrapper.get('[role="status"]').text()).toContain('불러오는 중')
    wrapper.unmount()
  })

  it('공간을_조회하면_수용_인원과_서울_시각을_표시한다', async () => {
    list.mockResolvedValue(page)
    const wrapper = mount(SpaceListView, { global: { stubs: { RouterLink: RouterLinkStub } } })
    await flushPromises()
    expect(wrapper.text()).toContain('회의실 A')
    expect(wrapper.text()).toContain('6명')
    expect(wrapper.get('time').text()).toContain('09:00')
    expect(wrapper.text()).toContain('Asia/Seoul')
    wrapper.unmount()
  })

  it('공간이_없으면_빈_결과를_표시한다', async () => {
    list.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    const wrapper = mount(SpaceListView, { global: { stubs: { RouterLink: RouterLinkStub } } })
    await flushPromises()
    expect(wrapper.text()).toContain('등록된 공간이 없습니다')
    expect(wrapper.find('[aria-label="다음 페이지"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('실패하면_오류를_표시하고_재시도할_수_있다', async () => {
    list.mockRejectedValueOnce(new Error('연결할 수 없습니다.')).mockResolvedValue(page)
    const wrapper = mount(SpaceListView, { global: { stubs: { RouterLink: RouterLinkStub } } })
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('연결할 수 없습니다.')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('회의실 A')
    wrapper.unmount()
  })

  it('다음과_이전을_누르면_서버_페이지를_이동한다', async () => {
    list.mockResolvedValueOnce(page).mockResolvedValueOnce({ ...page, page: 1, content: [{ ...space, id: 21, name: '회의실 B' }] }).mockResolvedValueOnce(page)
    const wrapper = mount(SpaceListView, { global: { stubs: { RouterLink: RouterLinkStub } } })
    await flushPromises()
    expect(wrapper.get('[aria-label="이전 페이지"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[aria-label="다음 페이지"]').trigger('click')
    await flushPromises()
    expect(list).toHaveBeenLastCalledWith(1, expect.any(AbortSignal))
    expect(wrapper.text()).toContain('회의실 B')
    expect(wrapper.get('[aria-label="다음 페이지"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[aria-label="이전 페이지"]').trigger('click')
    await flushPromises()
    expect(list).toHaveBeenLastCalledWith(0, expect.any(AbortSignal))
    wrapper.unmount()
  })

  it('화면을_떠나면_진행_중인_요청을_취소한다', () => {
    list.mockReturnValue(new Promise(() => {}))
    const wrapper = mount(SpaceListView, { global: { stubs: { RouterLink: RouterLinkStub } } })
    const signal = list.mock.calls[0]![1]!
    wrapper.unmount()
    expect(signal.aborted).toBe(true)
  })
  it.each(['성공', '실패'])('새_목록보다_늦은_이전_%s를_반영하지_않는다', async (outcome) => {
    let resolve!: (value: typeof page) => void
    let reject!: (reason: Error) => void
    list.mockReturnValueOnce(new Promise((yes, no) => { resolve = yes; reject = no })).mockResolvedValueOnce({ ...page, content: [{ ...space, name: '최신 공간' }] })
    const wrapper = mount(SpaceListView, { global: { stubs: { RouterLink: RouterLinkStub } } })
    // 중단된 요청을 무시하는 서버를 재현해 두 GET을 직접 겹친다.
    await (wrapper.vm as unknown as { load: (page: number) => Promise<void> }).load(0)
    if (outcome === '성공') resolve(page); else reject(new Error('늦은 목록 오류'))
    await flushPromises()
    expect(wrapper.text()).toContain('최신 공간')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })
  it.each(['성공', '실패'])('목록_화면_이탈_후_늦은_%s가_상태를_갱신하지_않는다', async (outcome) => {
    let resolve!: (value: typeof page) => void
    let reject!: (reason: Error) => void
    list.mockReturnValueOnce(new Promise((yes, no) => { resolve = yes; reject = no }))
    const wrapper = mount(SpaceListView, { global: { stubs: { RouterLink: RouterLinkStub } } })
    const state = wrapper.vm as unknown as { error: string; result: unknown }
    wrapper.unmount()
    if (outcome === '성공') resolve(page); else reject(new Error('늦은 목록 오류'))
    await flushPromises()
    expect(state.result).toBeNull()
    expect(state.error).toBe('')
  })
})
