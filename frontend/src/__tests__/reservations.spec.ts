import { afterEach, describe, expect, it, vi } from 'vitest'
import { createReservation, seoulInputToOffset, validateReservation } from '../api/reservations'

const now = Date.parse('2026-11-10T00:00:00Z')
const input = { spaceId: '7', bookerName: '예약자', startAt: '2026-11-11T10:00', endAt: '2026-11-11T11:00' }
describe('예약 생성 API와 서울 시각 검증', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('서울_입력은_브라우저_시간대와_무관하게_명시적_오프셋으로_변환한다', () => {
    expect(seoulInputToOffset(input.startAt)).toBe('2026-11-11T10:00:00+09:00')
    expect(Date.parse(seoulInputToOffset(input.startAt)!)).toBe(Date.parse('2026-11-11T01:00:00Z'))
    expect(Date.parse(seoulInputToOffset('2026-11-11T00:30')!)).toBe(Date.parse('2026-11-10T15:30:00Z'))
  })
  it.each(['2026-02-30T10:00', '2026-13-01T10:00', '2026-11-11T25:00', '', '2026-11-11T10:00:00Z'])('잘못된_서울_입력_%s를_거부한다', value => {
    expect(seoulInputToOffset(value)).toBeNull()
  })
  it('유효한_미래_예약은_검증을_통과한다', () => {
    expect(validateReservation(input, now)).toEqual({})
  })
  it('필수값과_안전하지_않은_ID와_이름_길이를_검증한다', () => {
    expect(validateReservation({ ...input, spaceId: '', bookerName: ' ' }, now)).toEqual(expect.objectContaining({ spaceId: expect.any(String), bookerName: expect.any(String) }))
    expect(validateReservation({ ...input, spaceId: '9007199254740992', bookerName: 'a'.repeat(101) }, now)).toEqual(expect.objectContaining({ spaceId: expect.any(String), bookerName: expect.any(String) }))
    expect(validateReservation({ ...input, bookerName: 'a'.repeat(100) }, now)).toEqual({})
  })
  it('현재와_같거나_지난_시작과_시작_이하_종료를_거부한다', () => {
    expect(validateReservation({ ...input, startAt: '2026-11-10T09:00' }, now)).toHaveProperty('startAt')
    expect(validateReservation({ ...input, endAt: input.startAt }, now)).toHaveProperty('endAt')
    expect(validateReservation({ ...input, endAt: '2026-11-11T09:00' }, now)).toHaveProperty('endAt')
  })
  it('생성_요청은_숫자_ID와_오프셋_시각을_전송한다', async () => {
    const body = { id: 3, spaceId: 7, bookerName: '예약자', startAt: '2026-11-11T01:00:00Z', endAt: '2026-11-11T02:00:00Z', status: 'CONFIRMED', createdAt: '2026-11-10T00:00:00Z', cancelledAt: null }
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status: 201 }))
    vi.stubGlobal('fetch', fetch)
    expect(await createReservation(input)).toEqual(body)
    expect(fetch).toHaveBeenCalledWith('/api/reservations', expect.objectContaining({ method: 'POST', body: JSON.stringify({ spaceId: 7, bookerName: '예약자', startAt: '2026-11-11T10:00:00+09:00', endAt: '2026-11-11T11:00:00+09:00' }) }))
  })
  it('중복_409는_일반_통신_오류와_구분한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'RESERVATION_CONFLICT', message: '중복', fieldErrors: [] }), { status: 409 })))
    await expect(createReservation(input)).rejects.toMatchObject({ code: 'RESERVATION_CONFLICT', status: 409 })
  })
})
