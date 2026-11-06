<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { listSpaces, type SpacePage } from '../api/spaces'

const result = ref<SpacePage | null>(null)
const loading = ref(true)
const error = ref('')
let requestedPage = 0
let requestId = 0
let controller: AbortController | undefined

const seoulTime = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
})

async function load(page: number) {
  const id = ++requestId
  controller?.abort()
  controller = new AbortController()
  requestedPage = page
  loading.value = true
  error.value = ''
  result.value = null
  try {
    const response = await listSpaces(page, controller.signal)
    if (id === requestId) result.value = response
  } catch (cause) {
    if (id === requestId) error.value = cause instanceof Error ? cause.message : '목록을 불러오지 못했습니다.'
  } finally {
    if (id === requestId) loading.value = false
  }
}

onMounted(() => load(0))
onBeforeUnmount(() => { requestId++; controller?.abort() })
</script>

<template>
  <section aria-labelledby="spaces-title">
    <div class="page-heading">
      <div>
        <p class="eyebrow">공유 공간 둘러보기</p>
        <h1 id="spaces-title">함께할 공간을 찾아보세요.</h1>
        <p class="lead">회의와 모임을 위한 공간의 정보와 수용 인원을 확인하세요.</p>
      </div>
      <p class="timezone">시간 기준 <strong>Asia/Seoul</strong></p>
    </div>

    <div v-if="loading" class="notice" role="status">공간 목록을 불러오는 중입니다…</div>
    <div v-else-if="error" class="notice error-state">
      <p role="alert">{{ error }}</p>
      <button type="button" @click="load(requestedPage)">다시 시도</button>
    </div>
    <template v-else-if="result">
      <div class="list-summary" role="status">
        <p>전체 <strong>{{ result.totalElements }}</strong>개 공간</p>
        <span>공간 ID 오름차순 · 페이지당 20개</span>
      </div>
      <div v-if="result.content.length === 0" class="notice">
        <h2>{{ result.totalElements === 0 ? '등록된 공간이 없습니다.' : '이 페이지에 공간이 없습니다.' }}</h2>
        <p>등록된 공간은 이곳에서 확인할 수 있습니다.</p>
        <button v-if="result.page > 0" type="button" @click="load(0)">첫 페이지로 이동</button>
      </div>
      <ul v-else class="space-grid">
        <li v-for="space in result.content" :key="space.id" class="space-card">
          <div class="card-top"><span class="space-id">SPACE {{ String(space.id).padStart(3, '0') }}</span><span class="capacity">{{ space.capacity }}명</span></div>
          <h2>{{ space.name }}</h2>
          <p class="description">{{ space.description || '등록된 설명이 없습니다.' }}</p>
          <p class="created-at">등록 <time :datetime="space.createdAt">{{ seoulTime.format(new Date(space.createdAt)) }}</time></p>
        </li>
      </ul>
      <nav v-if="result.totalPages > 0" class="pagination" aria-label="공간 목록 페이지">
        <button type="button" aria-label="이전 페이지" :disabled="result.page === 0" @click="load(result.page - 1)">← 이전</button>
        <span>{{ result.page + 1 }} / {{ result.totalPages }} 페이지</span>
        <button type="button" aria-label="다음 페이지" :disabled="result.page + 1 >= result.totalPages || (result.page + 1) * 20 > 2147483647" @click="load(result.page + 1)">다음 →</button>
      </nav>
    </template>
  </section>
</template>
