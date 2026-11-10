import { afterEach, describe, expect, it, vi } from 'vitest'
import { listConfirmedIntervals } from '../api/reservations'

const reservation = { spaceId: 7, status: 'CONFIRMED', startAt: '2026-11-11T01:00:00Z', endAt: '2026-11-11T02:00:00Z' }
describe('확정 예약 현황 API', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('공간과_확정_상태와_조회_구간으로_모든_페이지를_조회한다', async () => {
    const fetch = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ content: [reservation], page: 0, size: 100, totalPages: 2 })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ content: [{ ...reservation, startAt: '2026-11-11T03:00:00Z', endAt: '2026-11-11T04:00:00Z' }], page: 1, size: 100, totalPages: 2 })))
    vi.stubGlobal('fetch', fetch)
    const result = await listConfirmedIntervals(7, '2026-11-11T00:00:00+09:00', '2026-11-12T00:30:00+09:00', new AbortController().signal)
    expect(result).toHaveLength(2)
    const query = new URL(fetch.mock.calls[0]![0], 'http://localhost').searchParams
    expect(query.get('spaceId')).toBe('7')
    expect(query.get('status')).toBe('CONFIRMED')
    expect(query.get('from')).toBe('2026-11-11T00:00:00+09:00')
    expect(new URL(fetch.mock.calls[1]![0], 'http://localhost').searchParams.get('page')).toBe('1')
  })
  it('예약이_없으면_빈_현황을_반환한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [], page: 0, size: 100, totalPages: 0 }))))
    expect(await listConfirmedIntervals(7, '2026-11-11T00:00:00Z', '2026-11-12T00:00:00Z', new AbortController().signal)).toEqual([])
  })
  it.each([{ ...reservation, status: 'CANCELLED' }, { ...reservation, spaceId: 8 }])('다른_공간이나_취소_예약_응답_%j는_예약_가능으로_잘못_처리하지_않는다', async invalid => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [invalid], page: 0, size: 100, totalPages: 1 }))))
    await expect(listConfirmedIntervals(7, '2026-11-11T00:00:00Z', '2026-11-12T00:00:00Z', new AbortController().signal)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
  it('마이크로초가_남은_종료는_다음_밀리초로_올려_빈_시간으로_보이지_않게_한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [{ ...reservation, endAt: '2026-11-11T02:00:00.000001Z' }], page: 0, size: 100, totalPages: 1 }))))
    const result = await listConfirmedIntervals(7, '2026-11-11T00:00:00Z', '2026-11-12T00:00:00Z', new AbortController().signal)
    expect(result[0]!.end).toBe(Date.parse('2026-11-11T02:00:00Z') + 1)
  })
})
