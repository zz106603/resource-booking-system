import { afterEach, describe, expect, it, vi } from 'vitest'
import { createSpace, getSpace, validateSpace } from '../api/spaces'

const space = { id: 7, name: '회의실', description: null, capacity: 6, createdAt: '2026-01-01T00:00:00Z' }
describe('공간 등록과 상세 API', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('유효한_입력은_정수로_전송하고_생성된_공간을_반환한다', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(space), { status: 201 }))
    vi.stubGlobal('fetch', fetch)
    expect(await createSpace({ name: '회의실', description: null, capacity: 6 })).toEqual(space)
    expect(fetch).toHaveBeenCalledWith('/api/spaces', expect.objectContaining({ method: 'POST', body: JSON.stringify({ name: '회의실', description: null, capacity: 6 }) }))
  })
  it('서버_필드_오류를_받으면_필드와_메시지를_보존한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'VALIDATION_ERROR', message: '입력 오류', fieldErrors: [{ field: 'name', message: '이름 오류' }] }), { status: 400 })))
    await expect(createSpace({ name: '회의실', description: null, capacity: 1 })).rejects.toMatchObject({ fieldErrors: [{ field: 'name', message: '이름 오류' }] })
  })
  it('존재하는_ID를_조회하면_상세를_반환한다', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(space)))
    vi.stubGlobal('fetch', fetch)
    expect(await getSpace('7')).toEqual(space)
    expect(fetch).toHaveBeenCalledWith('/api/spaces/7', expect.any(Object))
  })
  it.each(['0', '-1', 'abc', '1.5', '9007199254740992'])('지원하지_않는_ID_%s는_요청_전에_거부한다', async id => {
    const fetch = vi.fn()
    vi.stubGlobal('fetch', fetch)
    await expect(getSpace(id)).rejects.toMatchObject({ code: 'INVALID_REQUEST' })
    expect(fetch).not.toHaveBeenCalled()
  })
  it.each(['', '0', '-1', '1.5', 'abc', '2147483648'])('잘못된_수용_인원_%s는_필드_오류를_반환한다', capacity => {
    expect(validateSpace('공간', '', capacity)).toHaveProperty('capacity')
  })
  it('공백_이름과_길이_초과는_거부하고_허용_경계는_통과한다', () => {
    expect(validateSpace('  ', '', '1')).toHaveProperty('name')
    expect(validateSpace('a'.repeat(101), 'b'.repeat(2001), '1')).toEqual(expect.objectContaining({ name: expect.any(String), description: expect.any(String) }))
    expect(validateSpace('a'.repeat(100), 'b'.repeat(2000), '2147483647')).toEqual({})
  })
})
