export const halfHourOptions = Array.from({ length: 49 }, (_, index) =>
  `${String(Math.floor(index / 2)).padStart(2, '0')}:${index % 2 === 0 ? '00' : '30'}`)

export function combineSeoulDateTime(date: string, time: string): string {
  if (!date || !time) return ''
  if (time !== '24:00') return `${date}T${time}`
  const midnight = Date.parse(`${date}T00:00:00Z`)
  if (!Number.isFinite(midnight) || new Date(midnight).toISOString().slice(0, 10) !== date) return ''
  const nextDay = new Date(midnight + 86400000).toISOString()
  return /^\d{4}-/.test(nextDay) ? `${nextDay.slice(0, 10)}T00:00` : ''
}
