export class ApiError extends Error {
  constructor(public code: string, message: string, public status = 0) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function getJson(path: string, signal?: AbortSignal): Promise<unknown> {
  let response: Response
  try {
    response = await fetch(path, { headers: { Accept: 'application/json' }, signal })
  } catch (error) {
    if (signal?.aborted) throw error
    throw new ApiError('NETWORK_ERROR', '서버에 연결할 수 없습니다. 연결 상태를 확인하고 다시 시도해 주세요.')
  }
  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw new ApiError('INVALID_RESPONSE', '목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.', response.status)
  }
  if (!response.ok) {
    if (typeof body === 'object' && body !== null && 'code' in body && 'message' in body
      && typeof body.code === 'string' && typeof body.message === 'string') {
      throw new ApiError(body.code, body.message, response.status)
    }
    throw new ApiError('HTTP_ERROR', '목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.', response.status)
  }
  return body
}
