import { ApiError, getJson, postJson } from './client'

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

export interface CreateSpace { name: string; description: string | null; capacity: number }
export type SpaceErrors = Partial<Record<keyof CreateSpace, string>>

export function validateSpace(name: string, description: string, capacity: string): SpaceErrors {
  const errors: SpaceErrors = {}
  if (!name.trim()) errors.name = '공간 이름을 입력해 주세요.'
  else if (name.length > 100) errors.name = '이름은 100자 이하여야 합니다.'
  if (description.length > 2000) errors.description = '설명은 2,000자 이하여야 합니다.'
  const count = Number(capacity)
  if (!/^\d+$/.test(capacity) || !Number.isInteger(count) || count < 1 || count > 2147483647) {
    errors.capacity = '수용 인원은 1~2,147,483,647의 정수여야 합니다.'
  }
  return errors
}

function spaceResponse(body: unknown): Space {
  if (!isSpace(body)) throw new ApiError('INVALID_RESPONSE', '서버 응답 형식을 확인할 수 없습니다.')
  return body
}

export async function createSpace(input: CreateSpace): Promise<Space> {
  return spaceResponse(await postJson('/api/spaces', input))
}

export async function getSpace(id: string, signal?: AbortSignal): Promise<Space> {
  if (!/^\d+$/.test(id) || !Number.isSafeInteger(Number(id)) || Number(id) < 1) {
    throw new ApiError('INVALID_REQUEST', '올바른 공간 ID가 아닙니다.')
  }
  return spaceResponse(await getJson(`/api/spaces/${Number(id)}`, signal))
}
