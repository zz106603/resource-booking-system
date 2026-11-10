import { ApiError, getJson, postJson } from './client'

export interface ReservedInterval { start: number; end: number }

export async function listConfirmedIntervals(spaceId: number, from: string, to: string, signal: AbortSignal): Promise<ReservedInterval[]> {
  const intervals: ReservedInterval[] = []
  let pages = 1
  for (let page = 0; page < pages; page++) {
    const query = new URLSearchParams({ spaceId: String(spaceId), status: 'CONFIRMED', from, to, page: String(page), size: '100' })
    const body = await getJson(`/api/reservations?${query}`, signal)
    if (typeof body !== 'object' || body === null) throw new ApiError('INVALID_RESPONSE', '예약 현황을 확인할 수 없습니다.')
    const result = body as Record<string, unknown>
    if (!Array.isArray(result.content) || result.page !== page || result.size !== 100
      || typeof result.totalPages !== 'number' || !Number.isSafeInteger(result.totalPages)
      || result.totalPages < 0 || result.totalPages > Math.floor(2147483647 / 100) + 1) {
      throw new ApiError('INVALID_RESPONSE', '예약 현황을 확인할 수 없습니다.')
    }
    if (page === 0) pages = result.totalPages
    for (const item of result.content) {
      if (typeof item !== 'object' || item === null || item.spaceId !== spaceId || item.status !== 'CONFIRMED'
        || typeof item.startAt !== 'string' || typeof item.endAt !== 'string') {
        throw new ApiError('INVALID_RESPONSE', '예약 현황을 확인할 수 없습니다.')
      }
      const start = Date.parse(item.startAt)
      let end = Date.parse(item.endAt)
      // Keep sub-millisecond occupied ends from appearing free at a minute boundary.
      const fraction = item.endAt.match(/\.(\d+)(?:Z|[+-]\d{2}:\d{2})$/)?.[1] ?? ''
      if (/[1-9]/.test(fraction.slice(3))) end++
      if (!Number.isFinite(start) || !Number.isFinite(end) || start >= end) {
        throw new ApiError('INVALID_RESPONSE', '예약 현황을 확인할 수 없습니다.')
      }
      intervals.push({ start, end })
    }
  }
  return intervals
}

export interface ReservationInput { spaceId: string; bookerName: string; startAt: string; endAt: string }
export type ReservationErrors = Partial<Record<keyof ReservationInput, string>>
export interface Reservation {
  id: number; spaceId: number; bookerName: string; startAt: string; endAt: string
  status: 'CONFIRMED'; createdAt: string; cancelledAt: null
}

export function seoulInputToOffset(value: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) return null
  const offset = `${value}:00+09:00`
  const instant = Date.parse(offset)
  if (!Number.isFinite(instant)) return null
  // Round-trip calendar validation rejects Date.parse's normalization of e.g. February 30.
  if (new Date(instant + 9 * 3600000).toISOString().slice(0, 16) !== value) return null
  return offset
}

export function validateReservation(input: ReservationInput, now = Date.now()): ReservationErrors {
  const errors: ReservationErrors = {}
  const spaceId = Number(input.spaceId)
  if (!/^\d+$/.test(input.spaceId) || !Number.isSafeInteger(spaceId) || spaceId < 1) errors.spaceId = '예약할 공간을 선택해 주세요.'
  if (!input.bookerName.trim()) errors.bookerName = '예약자명을 입력해 주세요.'
  else if (input.bookerName.length > 100) errors.bookerName = '예약자명은 100자 이하여야 합니다.'
  const start = seoulInputToOffset(input.startAt)
  const end = seoulInputToOffset(input.endAt)
  if (!start) errors.startAt = '시작 날짜와 시간을 정확히 입력해 주세요.'
  else if (Date.parse(start) <= now) errors.startAt = '시작 시각은 현재보다 미래여야 합니다.'
  if (!end) errors.endAt = '종료 날짜와 시간을 정확히 입력해 주세요.'
  else if (start && Date.parse(end) <= Date.parse(start)) errors.endAt = '종료 시각은 시작 시각보다 늦어야 합니다.'
  return errors
}

export async function createReservation(input: ReservationInput): Promise<Reservation> {
  const startAt = seoulInputToOffset(input.startAt)
  const endAt = seoulInputToOffset(input.endAt)
  if (!startAt || !endAt) throw new ApiError('INVALID_REQUEST', '예약 시각을 확인해 주세요.')
  const body = await postJson('/api/reservations', { spaceId: Number(input.spaceId), bookerName: input.bookerName, startAt, endAt })
  if (typeof body !== 'object' || body === null) throw new ApiError('INVALID_RESPONSE', '예약 응답을 확인할 수 없습니다. 등록 여부를 확인해 주세요.')
  const value = body as Record<string, unknown>
  if (typeof value.id !== 'number' || !Number.isSafeInteger(value.id) || value.id < 1
    || value.spaceId !== Number(input.spaceId) || typeof value.bookerName !== 'string'
    || value.status !== 'CONFIRMED' || value.cancelledAt !== null
    || ![value.startAt, value.endAt, value.createdAt].every(v => typeof v === 'string' && Number.isFinite(Date.parse(v)))) {
    throw new ApiError('INVALID_RESPONSE', '예약 응답을 확인할 수 없습니다. 등록 여부를 확인해 주세요.')
  }
  return value as unknown as Reservation
}
