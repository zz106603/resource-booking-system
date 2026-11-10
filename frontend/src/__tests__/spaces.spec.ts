import { afterEach, describe, expect, it, vi } from 'vitest'
import { listSpaces } from '../api/spaces'

describe('공간 API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('페이지를_지정하면_정해진_크기로_목록을_요청한다', async () => {
    const body = { content: [], page: 1, size: 20, totalElements: 0, totalPages: 0 }
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(body)))
    vi.stubGlobal('fetch', fetch)
    expect(await listSpaces(1)).toEqual(body)
    expect(fetch).toHaveBeenCalledWith('/api/spaces?page=1&size=20', expect.any(Object))
  })

  it('서버가_공통_오류를_보내면_메시지와_코드를_보존한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'INVALID_REQUEST', message: '입력값을 확인해 주세요.', fieldErrors: [],
    }), { status: 400 })))
    await expect(listSpaces(0)).rejects.toMatchObject({ code: 'INVALID_REQUEST', message: '입력값을 확인해 주세요.' })
  })

  it('오류_응답이_JSON이_아니면_내부_본문을_노출하지_않는다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>secret</html>', { status: 502 })))
    await expect(listSpaces(0)).rejects.toThrow('요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  })

  it('안전_정수_범위를_넘는_ID는_잘못된_값으로_표시하지_않는다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      content: [{ id: Number.MAX_SAFE_INTEGER + 1, name: '공간', description: null, capacity: 1, createdAt: '2026-01-01T00:00:00Z' }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    }))))
    await expect(listSpaces(0)).rejects.toThrow('서버 응답 형식을 확인할 수 없습니다.')
  })
})
