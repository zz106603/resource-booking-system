import { ApiError, getJson } from './client'

export interface Space {
  id: number
  name: string
  description: string | null
  capacity: number
  createdAt: string
}

export interface SpacePage {
  content: Space[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isCount(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
}

function isSpace(value: unknown): value is Space {
  return isRecord(value) && isCount(value.id) && value.id > 0
    && typeof value.name === 'string'
    && (value.description === null || typeof value.description === 'string')
    && isCount(value.capacity) && value.capacity > 0 && value.capacity <= 2147483647
    && typeof value.createdAt === 'string' && Number.isFinite(Date.parse(value.createdAt))
}

export async function listSpaces(page: number, signal?: AbortSignal): Promise<SpacePage> {
  if (!isCount(page) || page * 20 > 2147483647) {
    throw new ApiError('INVALID_REQUEST', '지원하지 않는 페이지입니다.')
  }
  const body = await getJson(`/api/spaces?${new URLSearchParams({ page: String(page), size: '20' })}`, signal)
  if (!isRecord(body) || !Array.isArray(body.content) || !body.content.every(isSpace)
    || body.page !== page || body.size !== 20 || !isCount(body.totalElements) || !isCount(body.totalPages)) {
    throw new ApiError('INVALID_RESPONSE', '서버 응답 형식을 확인할 수 없습니다.')
  }
  return body as unknown as SpacePage
}
