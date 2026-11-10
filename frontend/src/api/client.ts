export interface FieldError { field: string; message: string }

export class ApiError extends Error {
  constructor(public code: string, message: string, public status = 0, public fieldErrors: FieldError[] = []) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function getJson(path: string, signal?: AbortSignal): Promise<unknown> {
  return requestJson(path, signal)
}

export async function postJson(path: string, body: unknown): Promise<unknown> {
  return requestJson(path, undefined, body)
}

async function requestJson(path: string, signal?: AbortSignal, payload?: unknown): Promise<unknown> {
  let response: Response
  try {
    response = await fetch(path, payload === undefined
      ? { headers: { Accept: 'application/json' }, signal }
      : { method: 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
  } catch (error) {
    if (signal?.aborted) throw error
    throw new ApiError('NETWORK_ERROR', '서버에 연결할 수 없습니다. 연결 상태를 확인하고 다시 시도해 주세요.')
  }
  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw new ApiError('INVALID_RESPONSE', '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.', response.status)
  }
  if (!response.ok) {
    if (typeof body === 'object' && body !== null && 'code' in body && 'message' in body
      && typeof body.code === 'string' && typeof body.message === 'string') {
      const fields = 'fieldErrors' in body && Array.isArray(body.fieldErrors)
        ? body.fieldErrors.filter((item): item is FieldError => typeof item === 'object' && item !== null && typeof item.field === 'string' && typeof item.message === 'string') : []
      throw new ApiError(body.code, body.message, response.status, fields)
    }
    throw new ApiError('HTTP_ERROR', '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.', response.status)
  }
  return body
}
