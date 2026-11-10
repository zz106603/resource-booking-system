import { afterEach, describe, expect, it, vi } from 'vitest'
import { cancelReservation, listReservations, validateReservationFilters } from '../api/reservations'
import { combineSeoulDateTime } from '../utils/seoulTime'

const row = { id: 7, spaceId: 3, bookerName: '예약자', status: 'CONFIRMED', startAt: '2026-11-11T01:00:00Z', endAt: '2026-11-11T02:00:00Z', createdAt: '2026-11-10T00:00:00Z', cancelledAt: null }
const empty = { spaceId: '', status: '', from: '', to: '' }
describe('예약 목록·취소 API', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('선택하지_않은_필터는_생략하고_페이지를_전송한다', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [row], page: 1, size: 20, totalElements: 21, totalPages: 2 })))
    vi.stubGlobal('fetch', fetch)
    const result = await listReservations(empty, 1)
    expect(result.content[0]).toEqual(row)
    expect(fetch).toHaveBeenCalledWith('/api/reservations?page=1&size=20', expect.any(Object))
  })
  it('필터는_AND로_전송하고_서울_오프셋의_플러스를_인코딩한다', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })))
    vi.stubGlobal('fetch', fetch)
    await listReservations({ spaceId: '3', status: 'CANCELLED', from: '2020-01-01T10:00', to: '2020-01-01T11:00' }, 0)
    const query = new URL(fetch.mock.calls[0]![0], 'http://localhost').searchParams
    expect(query.get('spaceId')).toBe('3')
    expect(query.get('status')).toBe('CANCELLED')
    expect(query.get('from')).toBe('2020-01-01T10:00:00+09:00')
  })
  it('과거_구간은_허용하고_한쪽_누락과_역전_구간은_거부한다', () => {
    expect(validateReservationFilters({ ...empty, from: '2020-01-01T10:00', to: '2020-01-01T11:00' })).toEqual({})
    expect(validateReservationFilters({ ...empty, from: '2020-01-01T10:00' })).toHaveProperty('to')
    expect(validateReservationFilters({ ...empty, from: '2020-01-01T11:00', to: '2020-01-01T10:00' })).toHaveProperty('to')
    expect(validateReservationFilters({ ...empty, status: 'UNKNOWN', spaceId: '-1' })).toEqual(expect.objectContaining({ spaceId: expect.any(String), status: expect.any(String) }))
  })
  it('24시는_다음날로_바꾸고_불완전한_입력은_비운다', () => {
    expect(combineSeoulDateTime('2026-12-31', '24:00')).toBe('2027-01-01T00:00')
    expect(combineSeoulDateTime('', '10:00')).toBe('')
  })
  it('취소는_POST로_요청하고_최초_취소시각을_반환한다', async () => {
    const cancelled = { ...row, status: 'CANCELLED', cancelledAt: '2026-11-10T00:01:00Z' }
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(cancelled)))
    vi.stubGlobal('fetch', fetch)
    expect(await cancelReservation(7)).toEqual(cancelled)
    expect(fetch).toHaveBeenCalledWith('/api/reservations/7/cancel', expect.objectContaining({ method: 'POST' }))
  })
  it.each([404, 409, 503])('취소_%s_오류는_상태와_코드를_보존한다', async status => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'RESERVATION_ALREADY_STARTED', message: '실패', fieldErrors: [] }), { status })))
    await expect(cancelReservation(7)).rejects.toMatchObject({ status })
  })
  it('취소시각이_없는_취소_응답은_거부한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ ...row, status: 'CANCELLED' }))))
    await expect(cancelReservation(7)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
})
