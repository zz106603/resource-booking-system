<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ApiError } from '../api/client'
import { getSpace, listSpaces, type Space, type SpacePage } from '../api/spaces'
import { createReservation, listConfirmedIntervals, seoulInputToOffset, validateReservation, type Reservation, type ReservationErrors, type ReservedInterval } from '../api/reservations'

const route = useRoute()
const spaces = ref<SpacePage | null>(null)
const selected = ref<Space | null>(null)
const spaceId = ref('')
const bookerName = ref('')
const startDate = ref('')
const startTime = ref('')
const endDate = ref('')
const endTime = ref('')
const timeOptions = Array.from({ length: 49 }, (_, index) =>
  `${String(Math.floor(index / 2)).padStart(2, '0')}:${index % 2 === 0 ? '00' : '30'}`)
function combineDateTime(date: string, time: string): string {
  if (!date || !time) return ''
  if (time !== '24:00') return `${date}T${time}`
  const midnight = Date.parse(`${date}T00:00:00Z`)
  if (!Number.isFinite(midnight) || new Date(midnight).toISOString().slice(0, 10) !== date) return ''
  const nextDay = new Date(midnight + 86400000).toISOString()
  return /^\d{4}-/.test(nextDay) ? `${nextDay.slice(0, 10)}T00:00` : ''
}
const startAt = computed(() => combineDateTime(startDate.value, startTime.value))
const endAt = computed(() => combineDateTime(endDate.value, endTime.value))
const loading = ref(false)
const preloading = ref(false)
const pageError = ref('')
const error = ref('')
const errors = ref<ReservationErrors>({})
const submitting = ref(false)
const receipt = ref<Reservation | null>(null)
const receiptSpace = ref('')
let pageRequest = 0
let selectionRequest = 0
let pageController: AbortController | undefined
let selectionController: AbortController | undefined
let requestedPage = 0
let active = true
const externalSelection = computed(() => selected.value && !spaces.value?.content.some(space => space.id === selected.value?.id))
const displayTime = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
const reserved = ref<ReservedInterval[]>([])
const availabilityLoading = ref(false)
const availabilityError = ref('')
const availabilityNotice = ref('')
const loadedAvailabilityKey = ref('')
const availabilityKey = computed(() => `${selected.value?.id ?? ''}|${startDate.value}|${endDate.value}`)
const availabilityReady = computed(() => loadedAvailabilityKey.value === availabilityKey.value && !!loadedAvailabilityKey.value && !availabilityLoading.value && !availabilityError.value)
let availabilityRequest = 0
let availabilityController: AbortController | undefined

async function refreshAvailability() {
  const request = ++availabilityRequest
  availabilityController?.abort()
  availabilityController = new AbortController()
  const key = availabilityKey.value
  loadedAvailabilityKey.value = ''
  reserved.value = []
  availabilityLoading.value = false
  availabilityError.value = ''
  availabilityNotice.value = ''
  const dates = [startDate.value, endDate.value].filter(Boolean).sort()
  if (!selected.value || dates.length === 0) return
  const from = seoulInputToOffset(`${dates[0]}T00:00`)
  const last = seoulInputToOffset(`${dates[dates.length - 1]}T00:00`)
  if (!from || !last) { availabilityError.value = '날짜를 정확히 입력해 주세요.'; return }
  // Include the following midnight's 30-minute start slot for the 24:00 option.
  const to = new Date(Date.parse(last) + 86400000 + 1800000).toISOString()
  availabilityLoading.value = true
  try {
    const intervals = await listConfirmedIntervals(selected.value.id, from, to, availabilityController.signal)
    if (active && request === availabilityRequest && key === availabilityKey.value) {
      reserved.value = intervals
      loadedAvailabilityKey.value = key
    }
  } catch (cause) {
    if (active && request === availabilityRequest) availabilityError.value = cause instanceof Error ? cause.message : '예약 현황을 불러오지 못했습니다.'
  } finally {
    if (active && request === availabilityRequest) availabilityLoading.value = false
  }
}
function instant(date: string, time: string): number {
  const offset = seoulInputToOffset(combineDateTime(date, time))
  return offset ? Date.parse(offset) : NaN
}
function overlaps(start: number, end: number): boolean {
  return reserved.value.some(item => start < item.end && end > item.start)
}
function startUnavailable(time: string): boolean {
  const start = instant(startDate.value, time)
  return !availabilityReady.value || !Number.isFinite(start) || overlaps(start, start + 1800000)
}
function endUnavailable(time: string): boolean {
  const start = instant(startDate.value, startTime.value)
  const end = instant(endDate.value, time)
  return !availabilityReady.value || !Number.isFinite(start) || !Number.isFinite(end) || end <= start || overlaps(start, end)
}
watch(availabilityKey, refreshAvailability, { flush: 'sync' })
watch([availabilityReady, startTime, endTime], () => {
  if (!availabilityReady.value) return
  if (startTime.value && startUnavailable(startTime.value)) {
    startTime.value = ''; endTime.value = ''
    availabilityNotice.value = '이미 예약된 시작 시간의 선택을 해제했습니다. 다른 시간을 선택해 주세요.'
  } else if (endTime.value && endUnavailable(endTime.value)) {
    endTime.value = ''
    availabilityNotice.value = '선택한 구간이 예약과 겹치거나 시간 순서가 맞지 않아 종료 시간 선택을 해제했습니다.'
  }
}, { flush: 'sync' })

async function loadSpaces(page = 0) {
  const request = ++pageRequest
  pageController?.abort()
  pageController = new AbortController()
  requestedPage = page
  loading.value = true
  pageError.value = ''
  try {
    const value = await listSpaces(page, pageController.signal)
    if (active && request === pageRequest) spaces.value = value
  } catch (cause) {
    if (active && request === pageRequest) pageError.value = cause instanceof Error ? cause.message : '공간을 불러오지 못했습니다.'
  } finally {
    if (active && request === pageRequest) loading.value = false
  }
}
function chooseSpace() {
  selected.value = spaces.value?.content.find(space => String(space.id) === spaceId.value)
    ?? (String(selected.value?.id) === spaceId.value ? selected.value : null)
}
watch(() => route.query.spaceId, async value => {
  const request = ++selectionRequest
  selectionController?.abort()
  selectionController = new AbortController()
  selected.value = null
  spaceId.value = ''
  receipt.value = null
  errors.value = {}
  error.value = submitting.value ? '이전 예약 요청의 처리 여부를 확인한 후 다시 예약해 주세요.' : ''
  submitting.value = false
  preloading.value = typeof value === 'string' && value !== ''
  if (!preloading.value) return
  try {
    const space = await getSpace(String(value), selectionController.signal)
    if (active && request === selectionRequest) { selected.value = space; spaceId.value = String(space.id) }
  } catch (cause) {
    if (active && request === selectionRequest) error.value = cause instanceof ApiError && cause.status === 404
      ? '선택한 공간이 존재하지 않습니다. 다른 공간을 선택해 주세요.' : cause instanceof Error ? cause.message : '선택한 공간을 확인하지 못했습니다.'
  } finally {
    if (active && request === selectionRequest) preloading.value = false
  }
}, { immediate: true })

async function submit() {
  if (submitting.value || receipt.value) return
  if (!availabilityReady.value) { error.value = '예약 현황을 먼저 불러온 뒤 시간을 선택해 주세요.'; return }
  const input = { spaceId: spaceId.value, bookerName: bookerName.value, startAt: startAt.value, endAt: endAt.value }
  errors.value = validateReservation(input)
  if (startTime.value && startUnavailable(startTime.value)) errors.value.startAt = '이미 예약된 시작 시간입니다.'
  if (endTime.value && endUnavailable(endTime.value)) errors.value.endAt = '선택한 구간이 기존 예약과 겹치거나 종료 시각이 올바르지 않습니다.'
  if (!selected.value) errors.value.spaceId = '예약할 공간을 선택해 주세요.'
  error.value = ''
  if (Object.keys(errors.value).length) { error.value = '입력값을 확인해 주세요.'; return }
  const request = selectionRequest
  const spaceName = selected.value!.name
  submitting.value = true
  try {
    const value = await createReservation(input)
    if (active && request === selectionRequest) { receipt.value = value; receiptSpace.value = spaceName }
  } catch (cause) {
    if (!active || request !== selectionRequest) return
    error.value = cause instanceof ApiError && cause.code === 'RESERVATION_CONFLICT'
      ? '이미 예약된 시간과 겹칩니다. 다른 시간이나 공간을 선택해 주세요.'
      : cause instanceof ApiError && cause.code === 'SPACE_NOT_FOUND'
        ? '공간이 존재하지 않습니다. 다른 공간을 선택해 주세요.'
        : cause instanceof ApiError && cause.status === 503
          ? '일시적인 서버 경합으로 예약하지 못했습니다. 잠시 후 다시 시도해 주세요.'
          : cause instanceof Error ? cause.message : '예약을 생성하지 못했습니다.'
    if (cause instanceof ApiError) {
      if (cause.code === 'RESERVATION_CONFLICT') void refreshAvailability()
      for (const field of cause.fieldErrors) {
        if (field.field === 'spaceId' || field.field === 'bookerName' || field.field === 'startAt' || field.field === 'endAt') errors.value[field.field] = field.message
      }
    }
  } finally {
    if (active && request === selectionRequest) submitting.value = false
  }
}
function newReservation() {
  receipt.value = null
  startDate.value = ''; startTime.value = ''; endDate.value = ''; endTime.value = ''
  error.value = ''; errors.value = {}
}
onMounted(() => loadSpaces())
onBeforeUnmount(() => { active = false; pageController?.abort(); selectionController?.abort(); availabilityController?.abort() })
</script>

<template>
  <section aria-labelledby="reservation-title">
    <RouterLink to="/spaces">← 공간 목록</RouterLink>
    <div class="page-heading form-heading"><div><p class="eyebrow">시간과 공간을 연결하세요</p><h1 id="reservation-title">예약 생성</h1><p class="lead">모든 입력·표시 시각은 Asia/Seoul (UTC+09:00) 기준입니다.</p></div></div>
    <article v-if="receipt" class="detail-panel" aria-labelledby="success-title">
      <h2 id="success-title" role="status">예약이 완료되었습니다.</h2>
      <dl><dt>예약 번호</dt><dd>{{ receipt.id }}</dd><dt>공간</dt><dd>{{ receiptSpace }} (ID {{ receipt.spaceId }})</dd><dt>예약자</dt><dd>{{ receipt.bookerName }}</dd><dt>시작 (Asia/Seoul)</dt><dd><time :datetime="receipt.startAt">{{ displayTime.format(new Date(receipt.startAt)) }}</time></dd><dt>종료 (Asia/Seoul)</dt><dd><time :datetime="receipt.endAt">{{ displayTime.format(new Date(receipt.endAt)) }}</time></dd></dl>
      <div class="form-actions"><RouterLink :to="`/spaces/${receipt.spaceId}`">공간 상세</RouterLink><button type="button" @click="newReservation">새 예약 입력</button></div>
    </article>
    <form v-else class="space-form" novalidate :aria-busy="submitting" @submit.prevent="submit">
      <p v-if="error" role="alert" class="field-error">{{ error }}</p>
      <fieldset :disabled="submitting">
        <div class="form-field">
          <label for="spaceId">공간 (필수)</label>
          <p v-if="loading || preloading" role="status">공간을 불러오는 중입니다…</p>
          <div v-if="pageError" class="field-error"><p role="alert">{{ pageError }}</p><button type="button" @click="loadSpaces(requestedPage)">공간 다시 불러오기</button></div>
          <select id="spaceId" v-model="spaceId" :disabled="loading || preloading" :aria-invalid="!!errors.spaceId" :aria-describedby="errors.spaceId ? 'spaceId-error' : undefined" @change="chooseSpace">
            <option value="">공간을 선택하세요</option>
            <option v-if="externalSelection && selected" :value="String(selected.id)">{{ selected.name }} · {{ selected.capacity }}명 (선택됨)</option>
            <option v-for="space in spaces?.content || []" :key="space.id" :value="String(space.id)">{{ space.name }} · {{ space.capacity }}명 · ID {{ space.id }}</option>
          </select>
          <p v-if="!loading && spaces?.totalElements === 0">등록된 공간이 없습니다. 공간 등록 후 다시 방문해 주세요.</p>
          <p v-if="selected" class="form-help">선택: {{ selected.name }} (ID {{ selected.id }})</p>
          <p v-if="errors.spaceId" id="spaceId-error" class="field-error">{{ errors.spaceId }}</p>
          <nav v-if="spaces && spaces.totalPages > 0" class="pagination" aria-label="공간 선택 페이지">
            <button type="button" aria-label="이전 공간 페이지" :disabled="loading || preloading || spaces.page === 0" @click="loadSpaces(spaces.page - 1)">← 이전</button><span>{{ spaces.page + 1 }} / {{ spaces.totalPages }}</span><button type="button" aria-label="다음 공간 페이지" :disabled="loading || preloading || spaces.page + 1 >= spaces.totalPages || (spaces.page + 1) * 20 > 2147483647" @click="loadSpaces(spaces.page + 1)">다음 →</button>
          </nav>
        </div>
        <div class="form-field"><label for="bookerName">예약자명 (필수 · 최대 100자)</label><input id="bookerName" v-model="bookerName" maxlength="100" required :aria-invalid="!!errors.bookerName" :aria-describedby="errors.bookerName ? 'bookerName-error' : undefined"><p v-if="errors.bookerName" id="bookerName-error" class="field-error">{{ errors.bookerName }}</p></div>
        <div class="availability-info">
          <p v-if="availabilityLoading" role="status">확정 예약 현황을 불러오는 중입니다…</p>
          <p v-else-if="availabilityError" role="alert" class="field-error">{{ availabilityError }} 예약 현황을 확인하기 전에는 시간을 선택할 수 없습니다.</p>
          <p v-else-if="availabilityReady" role="status">이미 예약된 시간과 겹치는 선택지는 비활성화됩니다.</p>
          <p v-else class="form-help">공간과 날짜를 선택하면 예약 가능한 시간을 확인합니다.</p>
          <p v-if="availabilityNotice" role="status" class="form-help">{{ availabilityNotice }}</p>
          <button v-if="selected && (startDate || endDate)" type="button" :disabled="availabilityLoading" @click="refreshAvailability">예약 현황 새로고침</button>
        </div>
        <div class="form-field">
          <div class="date-time-fields">
            <div><label for="startDate">시작 날짜 (Asia/Seoul · 필수)</label><input id="startDate" v-model="startDate" type="date" required :aria-invalid="!!errors.startAt" :aria-describedby="errors.startAt ? 'startAt-error' : 'time-help'"></div>
            <div><label for="startTime">시작 시간 (필수)</label><select id="startTime" v-model="startTime" required :disabled="!availabilityReady || !startDate" :aria-invalid="!!errors.startAt" :aria-describedby="errors.startAt ? 'startAt-error' : 'time-help'"><option value="">시간 선택</option><option v-for="time in timeOptions" :key="time" :value="time" :disabled="startUnavailable(time)">{{ time }}{{ availabilityReady && startUnavailable(time) ? ' (예약됨)' : '' }}</option></select></div>
          </div>
          <p v-if="errors.startAt" id="startAt-error" class="field-error">{{ errors.startAt }}</p>
        </div>
        <div class="form-field">
          <div class="date-time-fields">
            <div><label for="endDate">종료 날짜 (Asia/Seoul · 필수)</label><input id="endDate" v-model="endDate" type="date" required :aria-invalid="!!errors.endAt" :aria-describedby="errors.endAt ? 'endAt-error' : 'time-help'"></div>
            <div><label for="endTime">종료 시간 (필수)</label><select id="endTime" v-model="endTime" required :disabled="!availabilityReady || !endDate || !startTime" :aria-invalid="!!errors.endAt" :aria-describedby="errors.endAt ? 'endAt-error' : 'time-help'"><option value="">시간 선택</option><option v-for="time in timeOptions" :key="time" :value="time" :disabled="endUnavailable(time)">{{ time }}</option></select></div>
          </div>
          <p v-if="errors.endAt" id="endAt-error" class="field-error">{{ errors.endAt }}</p>
        </div>
      </fieldset>
      <p id="time-help" class="form-help">00:00~24:00에서 30분 단위로 선택합니다. 24:00은 선택한 날짜의 다음 날 00:00입니다. 시작은 최소 30분을 확보할 수 있는 시간, 종료는 선택한 시작부터 겹치지 않는 시간만 선택할 수 있습니다. 맞닿는 예약은 허용됩니다. 다른 사용자의 동시 예약은 최종 서버 검사에서 409가 될 수 있습니다.</p>
      <p class="form-help">요청은 자동 재시도하지 않습니다. 응답이 끊긴 경우 Swagger 예약 목록에서 생성 여부를 확인하세요. 이 화면을 떠나도 서버의 예약 처리는 취소되지 않습니다.</p>
      <p v-if="submitting" role="status">예약을 생성하는 중입니다…</p>
      <div class="form-actions"><RouterLink to="/spaces">목록으로</RouterLink><button type="submit" :disabled="submitting || preloading || !selected || !availabilityReady">{{ submitting ? '예약 중…' : '예약 생성' }}</button></div>
    </form>
  </section>
</template>
