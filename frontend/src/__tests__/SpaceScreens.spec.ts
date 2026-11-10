import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import SpaceCreateView from '../views/SpaceCreateView.vue'
import SpaceDetailView from '../views/SpaceDetailView.vue'
import { createSpace, getSpace } from '../api/spaces'
import { ApiError } from '../api/client'

vi.mock('../api/spaces', async importOriginal => ({ ...await importOriginal<typeof import('../api/spaces')>(), createSpace: vi.fn(), getSpace: vi.fn() }))
const space = { id: 7, name: '회의실', description: '설명', capacity: 6, createdAt: '2026-01-01T00:00:00Z' }
async function setup(path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/spaces/new', component: SpaceCreateView },
    { path: '/spaces/:id', component: SpaceDetailView },
    { path: '/spaces', component: { template: '<h1>목록</h1>' } },
  ] })
  await router.push(path)
  await router.isReady()
  const wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}
async function fill(wrapper: ReturnType<typeof mount>) {
  await wrapper.get('#name').setValue('회의실')
  await wrapper.get('#capacity').setValue('6')
}
describe('공간 등록·상세 화면', () => {
  afterEach(() => vi.resetAllMocks())
  it('입력이_잘못되면_필드_오류를_표시하고_등록하지_않는다', async () => {
    const { wrapper } = await setup('/spaces/new')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('#name-error').text()).toContain('이름')
    expect(vi.mocked(createSpace)).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('등록하면_반환된_ID의_상세_화면으로_이동한다', async () => {
    vi.mocked(createSpace).mockResolvedValue(space)
    vi.mocked(getSpace).mockResolvedValue(space)
    const { wrapper, router } = await setup('/spaces/new')
    await fill(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/spaces/7')
    expect(wrapper.text()).toContain('회의실')
    wrapper.unmount()
  })
  it('서버_검증에_실패하면_입력값과_필드_오류를_표시한다', async () => {
    vi.mocked(createSpace).mockRejectedValue(new ApiError('VALIDATION_ERROR', '입력 오류', 400, [{ field: 'name', message: '서버 이름 오류' }]))
    const { wrapper } = await setup('/spaces/new')
    await fill(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('#name-error').text()).toBe('서버 이름 오류')
    expect((wrapper.get('#name').element as HTMLInputElement).value).toBe('회의실')
    wrapper.unmount()
  })
  it('등록_중에는_중복_제출을_막는다', async () => {
    vi.mocked(createSpace).mockReturnValue(new Promise(() => {}))
    const { wrapper } = await setup('/spaces/new')
    await fill(wrapper)
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')
    expect(createSpace).toHaveBeenCalledTimes(1)
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
  it('상세를_조회하면_서울_시각과_목록_링크를_표시한다', async () => {
    vi.mocked(getSpace).mockResolvedValue(space)
    const { wrapper } = await setup('/spaces/7')
    expect(wrapper.get('time').text()).toContain('09:00')
    expect(wrapper.text()).toContain('Asia/Seoul')
    expect(wrapper.get('a[href="/spaces"]').attributes('href')).toBe('/spaces')
    wrapper.unmount()
  })
  it('공간이_없으면_404_안내를_표시한다', async () => {
    vi.mocked(getSpace).mockRejectedValue(new ApiError('SPACE_NOT_FOUND', '없음', 404))
    const { wrapper } = await setup('/spaces/7')
    expect(wrapper.get('[role="alert"]').text()).toContain('존재하지 않는 공간')
    wrapper.unmount()
  })
  it('상세_통신이_실패하면_재시도할_수_있다', async () => {
    vi.mocked(getSpace).mockRejectedValueOnce(new Error('통신 실패')).mockResolvedValueOnce(space)
    const { wrapper } = await setup('/spaces/7')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('회의실')
    wrapper.unmount()
  })
  it('경로가_바뀌면_늦게_온_이전_상세_응답을_무시한다', async () => {
    let resolve!: (value: typeof space) => void
    vi.mocked(getSpace).mockReturnValueOnce(new Promise(r => { resolve = r })).mockResolvedValueOnce({ ...space, id: 8, name: '다른 공간' })
    const { wrapper, router } = await setup('/spaces/7')
    await router.push('/spaces/8')
    await flushPromises()
    resolve(space)
    await flushPromises()
    expect(wrapper.text()).toContain('다른 공간')
    expect(wrapper.text()).not.toContain('회의실')
    wrapper.unmount()
  })
  it.each(['성공', '실패'])('등록_중_화면을_떠나면_늦은_%s가_이동하거나_오류를_남기지_않는다', async (outcome) => {
    let resolve!: (value: typeof space) => void
    let reject!: (reason: Error) => void
    vi.mocked(createSpace).mockReturnValueOnce(new Promise((yes, no) => { resolve = yes; reject = no }))
    const { wrapper, router } = await setup('/spaces/new')
    const state = wrapper.findComponent(SpaceCreateView).vm as unknown as { error: string }
    await fill(wrapper)
    await wrapper.get('form').trigger('submit')
    await router.push('/spaces')
    await flushPromises()
    if (outcome === '성공') resolve(space); else reject(new Error('늦은 등록 오류'))
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/spaces')
    expect(wrapper.text()).toBe('목록')
    expect(state.error).toBe('')
    wrapper.unmount()
  })
  it('경로가_바뀌면_늦은_이전_상세_실패가_최신_공간을_덮어쓰지_않는다', async () => {
    let reject!: (reason: Error) => void
    vi.mocked(getSpace).mockReturnValueOnce(new Promise((_, no) => { reject = no })).mockResolvedValueOnce({ ...space, id: 8, name: '최신 공간' })
    const { wrapper, router } = await setup('/spaces/7')
    await router.push('/spaces/8')
    await flushPromises()
    reject(new Error('늦은 상세 오류'))
    await flushPromises()
    expect(wrapper.text()).toContain('최신 공간')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })
  it.each(['성공', '실패'])('상세_화면을_떠나면_늦은_%s가_상태를_갱신하지_않는다', async (outcome) => {
    let resolve!: (value: typeof space) => void
    let reject!: (reason: Error) => void
    vi.mocked(getSpace).mockReturnValueOnce(new Promise((yes, no) => { resolve = yes; reject = no }))
    const { wrapper, router } = await setup('/spaces/7')
    const state = wrapper.findComponent(SpaceDetailView).vm as unknown as { error: string; space: unknown }
    await router.push('/spaces')
    await flushPromises()
    if (outcome === '성공') resolve(space); else reject(new Error('늦은 상세 오류'))
    await flushPromises()
    expect(state.space).toBeNull()
    expect(state.error).toBe('')
    expect(wrapper.text()).toBe('목록')
    wrapper.unmount()
  })
})
