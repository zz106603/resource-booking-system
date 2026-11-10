<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import SpaceFilterSelect from '../components/SpaceFilterSelect.vue'
import { ApiError } from '../api/client'
import { cancelReservation, listReservations, validateReservationFilters, type FilterErrors, type Reservation, type ReservationFilters, type ReservationPage } from '../api/reservations'
import { combineSeoulDateTime, halfHourOptions } from '../utils/seoulTime'

const emptyFilters = (): ReservationFilters => ({ spaceId: '', status: '', from: '', to: '' })
const draft = reactive(emptyFilters())
const dates = reactive({ from: '', fromTime: '', to: '', toTime: '' })
const errors = ref<FilterErrors>({})
let applied = emptyFilters()
const result = ref<ReservationPage | null>(null)
const loading = ref(false)
const listError = ref('')
const notice = ref('')
const noticeElement = ref<HTMLElement | null>(null)
const confirmation = ref<Reservation | null>(null)
const confirmTitle = ref<HTMLElement | null>(null)
let cancelOpener: HTMLButtonElement | null = null
const cancelling = ref(false)
const cancelError = ref('')
let page = 0
let requestId = 0
let controller: AbortController | undefined
let active = true
const formatter = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
const time = (value: string) => formatter.format(new Date(value))
const canCancel = (row: Reservation) => row.status === 'CONFIRMED' && Date.parse(row.startAt) > Date.now()

async function load(target = page) {
  const id = ++requestId
  controller?.abort(); controller = new AbortController()
  const filter = { ...applied }
  loading.value = true; listError.value = ''; result.value = null; confirmation.value = null; cancelError.value = ''
  page = target
  try {
    let value = await listReservations(filter, target, controller.signal)
    if (!active || id !== requestId) return
    if (value.content.length === 0 && target > 0 && target >= value.totalPages) {
      target = Math.max(0, value.totalPages - 1)
      value = await listReservations(filter, target, controller.signal)
    }
    if (active && id === requestId) { result.value = value; page = target }
  } catch (cause) {
    if (active && id === requestId) listError.value = cause instanceof ApiError && cause.code === 'SPACE_NOT_FOUND' ? '조회할 공간이 존재하지 않습니다. 필터를 초기화하거나 다른 공간을 선택해 주세요.' : cause instanceof Error ? cause.message : '예약 목록을 불러오지 못했습니다.'
  } finally { if (active && id === requestId) loading.value = false }
}
function applyFilters() {
  if (cancelling.value) return
  const filters = { ...draft, from: combineSeoulDateTime(dates.from, dates.fromTime), to: combineSeoulDateTime(dates.to, dates.toTime) }
  errors.value = validateReservationFilters(filters)
  if ((dates.from || dates.fromTime) && !filters.from) errors.value.from = '조회 시작 날짜와 시간을 모두 정확히 입력해 주세요.'
  if ((dates.to || dates.toTime) && !filters.to) errors.value.to = '조회 종료 날짜와 시간을 모두 정확히 입력해 주세요.'
  if (Object.keys(errors.value).length) return
  applied = filters; notice.value = ''; void load(0)
}
function resetFilters() {
  if (cancelling.value) return
  Object.assign(draft, emptyFilters()); Object.assign(dates, { from: '', fromTime: '', to: '', toTime: '' })
  errors.value = {}; applied = emptyFilters(); notice.value = ''; void load(0)
}
function askCancel(row: Reservation, event: Event) {
  if (cancelling.value || !canCancel(row)) return
  cancelOpener = event.currentTarget as HTMLButtonElement
  confirmation.value = row; cancelError.value = ''
  void nextTick(() => { if (active && confirmation.value?.id === row.id) confirmTitle.value?.focus() })
}
function dismissCancel() {
  if (cancelling.value) return
  confirmation.value = null
  void nextTick(() => { if (active && cancelOpener?.isConnected && !cancelOpener.disabled) cancelOpener.focus() })
}
async function confirmCancel() {
  if (!confirmation.value || cancelling.value) return
  const row = confirmation.value
  cancelling.value = true; cancelError.value = ''; notice.value = ''
  try {
    await cancelReservation(row.id)
    if (!active) return
    notice.value = `예약 #${row.id}이 취소되었습니다. 목록을 다시 조회합니다.`
    confirmation.value = null
    await nextTick()
    if (!active) return
    noticeElement.value?.focus()
    await load(page)
  } catch (cause) {
    if (!active) return
    cancelError.value = cause instanceof ApiError && cause.status === 409 ? '이미 시작된 예약은 취소할 수 없습니다. 목록을 새로고침해 주세요.'
      : cause instanceof ApiError && cause.status === 404 ? '예약이 존재하지 않습니다. 목록을 새로고침해 주세요.'
        : cause instanceof ApiError && cause.status === 503 ? '일시적인 서버 오류입니다. 잠시 후 목록을 확인하고 다시 시도해 주세요.'
          : cause instanceof Error ? cause.message : '취소 결과를 확인하지 못했습니다. 목록을 새로고침해 주세요.'
  } finally { if (active) cancelling.value = false }
}
onMounted(() => load(0))
onBeforeUnmount(() => { active = false; requestId++; controller?.abort() })
</script>

<template>
  <section aria-labelledby="reservations-title">
    <div class="page-heading"><div><p class="eyebrow">예약 관리</p><h1 id="reservations-title">예약 목록</h1><p class="lead">공간·상태·겹치는 조회 구간으로 예약을 확인하세요.</p></div><RouterLink class="button-link" to="/reservations/new">새 예약</RouterLink></div>
    <form class="reservation-filters" novalidate @submit.prevent="applyFilters">
      <fieldset :disabled="loading || cancelling">
        <div class="filter-grid">
          <div class="form-field"><SpaceFilterSelect v-model="draft.spaceId" /><p v-if="errors.spaceId" class="field-error">{{ errors.spaceId }}</p></div>
          <div class="form-field"><label for="filter-status">상태</label><select id="filter-status" v-model="draft.status"><option value="">전체 상태</option><option value="CONFIRMED">확정</option><option value="CANCELLED">취소</option></select><p v-if="errors.status" class="field-error">{{ errors.status }}</p></div>
          <div class="form-field"><label for="from-date">조회 시작 날짜</label><input id="from-date" v-model="dates.from" type="date" :aria-invalid="!!errors.from" :aria-describedby="errors.from ? 'from-error' : 'filter-help'"><label for="from-time">조회 시작 시간</label><select id="from-time" v-model="dates.fromTime" :aria-invalid="!!errors.from" :aria-describedby="errors.from ? 'from-error' : 'filter-help'"><option value="">미지정</option><option v-for="value in halfHourOptions" :key="value" :value="value">{{ value }}</option></select><p v-if="errors.from" id="from-error" class="field-error" role="alert">{{ errors.from }}</p></div>
          <div class="form-field"><label for="to-date">조회 종료 날짜</label><input id="to-date" v-model="dates.to" type="date" :aria-invalid="!!errors.to" :aria-describedby="errors.to ? 'to-error' : 'filter-help'"><label for="to-time">조회 종료 시간</label><select id="to-time" v-model="dates.toTime" :aria-invalid="!!errors.to" :aria-describedby="errors.to ? 'to-error' : 'filter-help'"><option value="">미지정</option><option v-for="value in halfHourOptions" :key="value" :value="value">{{ value }}</option></select><p v-if="errors.to" id="to-error" class="field-error" role="alert">{{ errors.to }}</p></div>
        </div>
        <p id="filter-help" class="form-help">Asia/Seoul 기준입니다. 조회 시작·종료를 모두 입력하거나 모두 비워 주세요. 24:00은 다음 날 00:00이며 과거 조회도 가능합니다.</p>
        <div class="filter-actions"><button type="submit">필터 적용</button><button type="button" @click="resetFilters">필터 초기화</button></div>
      </fieldset>
    </form>
    <p v-if="notice" ref="noticeElement" role="status" class="notice" tabindex="-1">{{ notice }}</p>
    <section v-if="confirmation" class="cancel-confirmation" aria-labelledby="cancel-title">
      <h2 id="cancel-title" ref="confirmTitle" tabindex="-1">이 예약을 취소할까요?</h2><p>예약 #{{ confirmation.id }} · 공간 ID {{ confirmation.spaceId }} · {{ confirmation.bookerName }}</p><p>{{ time(confirmation.startAt) }} ~ {{ time(confirmation.endAt) }} (Asia/Seoul)</p>
      <p v-if="cancelError" role="alert" class="field-error">{{ cancelError }}</p><p v-if="cancelling" role="status">취소하는 중입니다…</p>
      <div class="filter-actions"><button data-confirm type="button" :disabled="cancelling" @click="confirmCancel">취소 확정</button><button data-dismiss type="button" :disabled="cancelling" @click="dismissCancel">돌아가기</button></div>
    </section>
    <div class="list-summary"><span>Asia/Seoul · 시작 시각/ID 오름차순 · 페이지당 20개</span><button type="button" :disabled="loading || cancelling" @click="load()">목록 새로고침</button></div>
    <p v-if="loading" class="notice" role="status">예약 목록을 불러오는 중입니다…</p>
    <div v-else-if="listError" class="notice error-state"><p role="alert">{{ listError }}</p><button type="button" :disabled="cancelling" @click="load()">다시 시도</button></div>
    <template v-else-if="result">
      <p>전체 {{ result.totalElements }}개 예약</p>
      <p v-if="!result.content.length" class="notice">조건에 맞는 예약이 없습니다.</p>
      <div v-else class="table-scroll" role="region" aria-label="예약 목록 표" tabindex="0">
        <table class="reservation-table"><caption>예약 목록 (Asia/Seoul)</caption><thead><tr><th scope="col">예약</th><th scope="col">공간</th><th scope="col">예약자</th><th scope="col">상태</th><th scope="col">시작</th><th scope="col">종료</th><th scope="col">취소 시각</th><th scope="col">작업</th></tr></thead><tbody>
          <tr v-for="row in result.content" :key="row.id"><td>#{{ row.id }}</td><td><RouterLink :to="`/spaces/${row.spaceId}`">공간 ID {{ row.spaceId }}</RouterLink></td><td>{{ row.bookerName }}</td><td>{{ row.status === 'CONFIRMED' ? '확정' : '취소' }}</td><td><time :datetime="row.startAt">{{ time(row.startAt) }}</time></td><td><time :datetime="row.endAt">{{ time(row.endAt) }}</time></td><td><time v-if="row.cancelledAt" :datetime="row.cancelledAt">{{ time(row.cancelledAt) }}</time><span v-else>—</span></td><td><button type="button" :data-cancel="row.id" :disabled="cancelling || !canCancel(row)" @click="askCancel(row, $event)">{{ row.status === 'CANCELLED' ? '취소됨' : canCancel(row) ? '취소' : '시작됨' }}</button></td></tr>
        </tbody></table>
      </div>
      <nav v-if="result.totalPages > 0" class="pagination" aria-label="예약 목록 페이지"><button type="button" aria-label="이전 예약 페이지" :disabled="cancelling || result.page === 0" @click="load(result.page - 1)">← 이전</button><span>{{ result.page + 1 }} / {{ result.totalPages }}</span><button type="button" aria-label="다음 예약 페이지" :disabled="cancelling || result.page + 1 >= result.totalPages || (result.page + 1) * 20 > 2147483647" @click="load(result.page + 1)">다음 →</button></nav>
    </template>
    <p class="form-help">취소 가능 여부의 최종 판단은 서버가 수행합니다. 응답이 끊기면 목록을 새로고침해 결과를 확인하세요. 취소 요청은 자동 재시도하지 않습니다.</p>
  </section>
</template>
