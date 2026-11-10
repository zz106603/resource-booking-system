import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ReservationCreateView from '../views/ReservationCreateView.vue'
import { listSpaces, getSpace } from '../api/spaces'
import { createReservation, listConfirmedIntervals } from '../api/reservations'
import { ApiError } from '../api/client'

vi.mock('../api/spaces', () => ({ listSpaces: vi.fn(), getSpace: vi.fn() }))
vi.mock('../api/reservations', async original => ({ ...await original<typeof import('../api/reservations')>(), createReservation: vi.fn(), listConfirmedIntervals: vi.fn() }))
const space = { id: 7, name: '회의실', description: null, capacity: 6, createdAt: '2026-01-01T00:00:00Z' }
const page = { content: [space], page: 0, size: 20, totalElements: 21, totalPages: 2 }
const receipt = { id: 31, spaceId: 7, bookerName: '예약자', startAt: '2026-11-11T01:00:00Z', endAt: '2026-11-11T02:00:00Z', status: 'CONFIRMED' as const, createdAt: '2026-11-10T00:00:00Z', cancelledAt: null }
async function setup(query = '') {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/reservations/new', component: ReservationCreateView },
    { path: '/spaces', component: { template: '<h1>목록</h1>' } },
    { path: '/spaces/:id', component: { template: '<h1>상세</h1>' } },
  ] })
  await router.push('/reservations/new' + query)
  await router.isReady()
  const wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}
async function fill(wrapper: ReturnType<typeof mount>) {
  await wrapper.get('#spaceId').setValue('7')
  await wrapper.get('#bookerName').setValue('예약자')
  await wrapper.get('#startDate').setValue('2026-11-11')
  await wrapper.get('#endDate').setValue('2026-11-11')
  await flushPromises()
  await wrapper.get('#startTime').setValue('10:00')
  await wrapper.get('#endTime').setValue('11:00')
}
describe('예약 생성 화면', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-10T00:00:00Z'))
    vi.mocked(listSpaces).mockResolvedValue(page)
    vi.mocked(listConfirmedIntervals).mockResolvedValue([])
  })
  afterEach(() => { vi.resetAllMocks(); vi.useRealTimers() })
  it('공간_선택과_서울_시간대_안내를_표시한다', async () => {
    const { wrapper } = await setup()
    expect(wrapper.get('#spaceId').text()).toContain('회의실')
    expect(wrapper.text()).toContain('Asia/Seoul')
    wrapper.unmount()
  })
  it('공간이_없으면_빈_상태를_안내하고_제출을_막는다', async () => {
    vi.mocked(listSpaces).mockResolvedValue({ ...page, content: [], totalElements: 0, totalPages: 0 })
    const { wrapper } = await setup()
    expect(wrapper.text()).toContain('등록된 공간이 없습니다')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
  it('첫_페이지_밖_공간도_사전_선택할_수_있다', async () => {
    vi.mocked(getSpace).mockResolvedValue({ ...space, id: 99, name: '선택된 공간' })
    const { wrapper } = await setup('?spaceId=99')
    expect(getSpace).toHaveBeenCalledWith('99', expect.any(AbortSignal))
    expect((wrapper.get('#spaceId').element as HTMLSelectElement).value).toBe('99')
    expect(wrapper.text()).toContain('선택된 공간')
    wrapper.unmount()
  })
  it('공간_목록의_다음_페이지를_조회한다', async () => {
    vi.mocked(listSpaces).mockResolvedValueOnce(page).mockResolvedValueOnce({ ...page, page: 1, content: [{ ...space, id: 21 }] })
    const { wrapper } = await setup()
    await wrapper.get('[aria-label="다음 공간 페이지"]').trigger('click')
    await flushPromises()
    expect(listSpaces).toHaveBeenLastCalledWith(1, expect.any(AbortSignal))
    wrapper.unmount()
  })
  it('잘못된_입력은_요청하지_않고_오류를_표시한다', async () => {
    const { wrapper } = await setup()
    await fill(wrapper)
    await wrapper.get('#bookerName').setValue(' ')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('#bookerName-error').text()).toContain('예약자')
    expect(createReservation).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('409_중복이면_시간_변경을_안내하고_입력을_유지한다', async () => {
    vi.mocked(createReservation).mockRejectedValue(new ApiError('RESERVATION_CONFLICT', '중복', 409))
    const { wrapper } = await setup()
    await fill(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('이미 예약된 시간')
    expect((wrapper.get('#startDate').element as HTMLInputElement).value).toBe('2026-11-11')
    expect((wrapper.get('#startTime').element as HTMLSelectElement).value).toBe('10:00')
    expect(listConfirmedIntervals).toHaveBeenCalledTimes(3)
    wrapper.unmount()
  })
  it('성공하면_예약_번호와_서울_시간을_표시한다', async () => {
    vi.mocked(createReservation).mockResolvedValue(receipt)
    const { wrapper } = await setup()
    await fill(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('예약이 완료되었습니다')
    expect(wrapper.text()).toContain('31')
    expect(wrapper.text()).toContain('10:00')
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.unmount()
  })
  it('제출_중에는_중복_요청을_차단한다', async () => {
    vi.mocked(createReservation).mockReturnValue(new Promise(() => {}))
    const { wrapper } = await setup()
    await fill(wrapper)
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')
    expect(createReservation).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
  it('서버_필드_오류를_해당_입력에_표시한다', async () => {
    vi.mocked(createReservation).mockRejectedValue(new ApiError('VALIDATION_ERROR', '검증 실패', 400, [{ field: 'bookerName', message: '예약자명 오류' }]))
    const { wrapper } = await setup()
    await fill(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('#bookerName-error').text()).toBe('예약자명 오류')
    wrapper.unmount()
  })
  it('공간_조회가_실패하면_재조회해_선택할_수_있다', async () => {
    vi.mocked(listSpaces).mockRejectedValueOnce(new Error('공간 통신 실패')).mockResolvedValueOnce(page)
    const { wrapper } = await setup()
    expect(wrapper.get('[role="alert"]').text()).toContain('공간 통신 실패')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.get('#spaceId').text()).toContain('회의실')
    wrapper.unmount()
  })
  it('다음_페이지로_이동해도_이미_선택한_공간을_보존한다', async () => {
    vi.mocked(listSpaces).mockResolvedValueOnce(page).mockResolvedValueOnce({ ...page, page: 1, content: [{ ...space, id: 21 }] })
    const { wrapper } = await setup()
    await wrapper.get('#spaceId').setValue('7')
    await wrapper.get('[aria-label="다음 공간 페이지"]').trigger('click')
    await flushPromises()
    expect((wrapper.get('#spaceId').element as HTMLSelectElement).value).toBe('7')
    expect(wrapper.get('#spaceId').text()).toContain('선택됨')
    wrapper.unmount()
  })
  it('존재하지_않는_사전선택_공간은_안내하고_다른_공간을_선택하게_한다', async () => {
    vi.mocked(getSpace).mockRejectedValue(new ApiError('SPACE_NOT_FOUND', '없음', 404))
    const { wrapper } = await setup('?spaceId=99')
    expect(wrapper.get('[role="alert"]').text()).toContain('존재하지 않습니다')
    await wrapper.get('#spaceId').setValue('7')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await fill(wrapper)
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })
  it('503_실패는_입력을_유지하고_자동_재시도하지_않는다', async () => {
    vi.mocked(createReservation).mockRejectedValue(new ApiError('TEMPORARY_DATABASE_FAILURE', '재시도', 503))
    const { wrapper } = await setup()
    await fill(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('일시적인 서버 경합')
    expect(createReservation).toHaveBeenCalledTimes(1)
    expect((wrapper.get('#bookerName').element as HTMLInputElement).value).toBe('예약자')
    wrapper.unmount()
  })
  it('시간_선택은_오전오후_없이_30분_간격의_00시부터_24시까지다', async () => {
    const { wrapper } = await setup()
    const values = wrapper.findAll('#startTime option').map(option => option.attributes('value')).filter(Boolean)
    expect(values).toHaveLength(49)
    expect(values.slice(0, 3)).toEqual(['00:00', '00:30', '01:00'])
    expect(values.slice(-2)).toEqual(['23:30', '24:00'])
    expect(wrapper.get('#startTime').text()).not.toMatch(/오전|오후/)
    wrapper.unmount()
  })
  it('24시는_다음_날_00시로_정규화해_제출한다', async () => {
    vi.mocked(createReservation).mockResolvedValue(receipt)
    const { wrapper } = await setup()
    await fill(wrapper)
    await wrapper.get('#startTime').setValue('23:30')
    await wrapper.get('#endTime').setValue('24:00')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(createReservation).toHaveBeenCalledWith(expect.objectContaining({ startAt: '2026-11-11T23:30', endAt: '2026-11-12T00:00' }))
    wrapper.unmount()
  })
  it('예약과_겹치는_시작과_종료는_막고_맞닿는_구간은_허용한다', async () => {
    vi.mocked(listConfirmedIntervals).mockResolvedValue([{ start: Date.parse('2026-11-11T01:30:00Z'), end: Date.parse('2026-11-11T02:30:00Z') }])
    const { wrapper } = await setup()
    await fill(wrapper)
    expect(wrapper.get('#startTime option[value="10:30"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('#startTime option[value="11:30"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('#endTime option[value="10:30"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('#endTime option[value="11:00"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
  it('현황_조회_중에는_선택과_제출을_차단한다', async () => {
    vi.mocked(listConfirmedIntervals).mockReturnValue(new Promise(() => {}))
    const { wrapper } = await setup()
    await wrapper.get('#spaceId').setValue('7')
    await wrapper.get('#startDate').setValue('2026-11-11')
    expect(wrapper.get('#startTime').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
  it('현황_실패_후_새로고침하면_시간을_선택할_수_있다', async () => {
    vi.mocked(listConfirmedIntervals).mockRejectedValueOnce(new Error('예약 현황 오류')).mockResolvedValue([])
    const { wrapper } = await setup()
    await wrapper.get('#spaceId').setValue('7')
    await wrapper.get('#startDate').setValue('2026-11-11')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('예약 현황 오류')
    expect(wrapper.get('#startTime').attributes('disabled')).toBeDefined()
    const refresh = wrapper.findAll('button').find(button => button.text() === '예약 현황 새로고침')!
    await refresh.trigger('click')
    await flushPromises()
    expect(wrapper.get('#startTime').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })
  it('새로_예약된_시간은_선택에서_해제한다', async () => {
    const { wrapper } = await setup()
    await fill(wrapper)
    vi.mocked(listConfirmedIntervals).mockResolvedValue([{ start: Date.parse('2026-11-11T01:00:00Z'), end: Date.parse('2026-11-11T02:00:00Z') }])
    await wrapper.findAll('button').find(button => button.text() === '예약 현황 새로고침')!.trigger('click')
    await flushPromises()
    expect((wrapper.get('#startTime').element as HTMLSelectElement).value).toBe('')
    expect((wrapper.get('#endTime').element as HTMLSelectElement).value).toBe('')
    expect(createReservation).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('날짜를_변경하면_취소를_무시한_늦은_이전_현황을_반영하지_않는다', async () => {
    let resolve!: (value: { start: number; end: number }[]) => void
    vi.mocked(listConfirmedIntervals).mockReturnValueOnce(new Promise(r => { resolve = r })).mockResolvedValue([])
    const { wrapper } = await setup()
    await wrapper.get('#spaceId').setValue('7')
    await wrapper.get('#startDate').setValue('2026-11-11')
    const previousSignal = vi.mocked(listConfirmedIntervals).mock.calls[0]![3]
    await wrapper.get('#startDate').setValue('2026-11-12')
    await flushPromises()
    resolve([{ start: Date.parse('2026-11-12T01:00:00Z'), end: Date.parse('2026-11-12T02:00:00Z') }])
    await flushPromises()
    expect(previousSignal.aborted).toBe(true)
    expect(wrapper.get('#startTime option[value="10:00"]').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })
  it.each(['성공', '실패'])('사전선택_query가_바뀌면_늦은_이전_%s를_무시한다', async (outcome) => {
    let resolve!: (value: typeof space) => void
    let reject!: (reason: Error) => void
    vi.mocked(getSpace).mockReturnValueOnce(new Promise((yes, no) => { resolve = yes; reject = no })).mockResolvedValueOnce({ ...space, id: 99, name: '최신 선택 공간' })
    const { wrapper, router } = await setup('?spaceId=7')
    await router.push('/reservations/new?spaceId=99')
    await flushPromises()
    if (outcome === '성공') resolve(space); else reject(new Error('늦은 선택 오류'))
    await flushPromises()
    expect((wrapper.get('#spaceId').element as HTMLSelectElement).value).toBe('99')
    expect(wrapper.text()).not.toContain('늦은 선택 오류')
    wrapper.unmount()
  })
  it.each(['성공', '실패'])('사전선택_중_화면을_떠나면_늦은_%s가_상태를_갱신하지_않는다', async (outcome) => {
    let resolve!: (value: typeof space) => void
    let reject!: (reason: Error) => void
    vi.mocked(getSpace).mockReturnValueOnce(new Promise((yes, no) => { resolve = yes; reject = no }))
    const { wrapper, router } = await setup('?spaceId=7')
    const state = wrapper.findComponent(ReservationCreateView).vm as unknown as { error: string; spaceId: string }
    await router.push('/spaces')
    await flushPromises()
    if (outcome === '성공') resolve(space); else reject(new Error('늦은 선택 오류'))
    await flushPromises()
    expect(state.spaceId).toBe('')
    expect(state.error).toBe('')
    expect(wrapper.text()).toBe('목록')
    wrapper.unmount()
  })
  it.each(['성공', '실패'])('예약_제출_후_화면을_떠나면_늦은_%s가_결과를_남기지_않는다', async (outcome) => {
    let resolve!: (value: typeof receipt) => void
    let reject!: (reason: Error) => void
    vi.mocked(createReservation).mockReturnValueOnce(new Promise((yes, no) => { resolve = yes; reject = no }))
    const { wrapper, router } = await setup()
    const state = wrapper.findComponent(ReservationCreateView).vm as unknown as { error: string; receipt: unknown }
    await fill(wrapper)
    await wrapper.get('form').trigger('submit')
    expect(createReservation).toHaveBeenCalledTimes(1)
    await router.push('/spaces')
    await flushPromises()
    if (outcome === '성공') resolve(receipt); else reject(new Error('늦은 예약 오류'))
    await flushPromises()
    expect(state.receipt).toBeNull()
    expect(state.error).toBe('')
    expect(wrapper.text()).toBe('목록')
    wrapper.unmount()
  })
  it.each(['성공', '실패'])('예약_제출_후_query가_바뀌면_이전_%s가_새_선택을_덮어쓰지_않는다', async (outcome) => {
    let resolve!: (value: typeof receipt) => void
    let reject!: (reason: Error) => void
    vi.mocked(createReservation).mockReturnValueOnce(new Promise((yes, no) => { resolve = yes; reject = no }))
    vi.mocked(getSpace).mockResolvedValue({ ...space, id: 99, name: '최신 선택 공간' })
    const { wrapper, router } = await setup()
    await fill(wrapper)
    await wrapper.get('form').trigger('submit')
    await router.push('/reservations/new?spaceId=99')
    await flushPromises()
    const state = wrapper.findComponent(ReservationCreateView).vm as unknown as { error: string; receipt: unknown }
    const message = state.error
    expect(message).toContain('이전 예약 요청')
    if (outcome === '성공') resolve(receipt); else reject(new Error('늦은 예약 오류'))
    await flushPromises()
    expect((wrapper.get('#spaceId').element as HTMLSelectElement).value).toBe('99')
    expect(state.receipt).toBeNull()
    expect(state.error).toBe(message)
    wrapper.unmount()
  })
})
