<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ApiError } from '../api/client'
import { getSpace, type Space } from '../api/spaces'

const route = useRoute()
const space = ref<Space | null>(null)
const loading = ref(false)
const error = ref('')
const retryable = ref(false)
let controller: AbortController | undefined
let requestId = 0
const time = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
async function load() {
  const id = ++requestId
  controller?.abort()
  controller = new AbortController()
  space.value = null
  error.value = ''
  loading.value = true
  try {
    const value = await getSpace(String(route.params.id), controller.signal)
    if (id === requestId) space.value = value
  } catch (cause) {
    if (id !== requestId) return
    retryable.value = !(cause instanceof ApiError && (cause.status === 404 || cause.code === 'INVALID_REQUEST'))
    error.value = cause instanceof ApiError && cause.status === 404 ? '존재하지 않는 공간입니다.' : cause instanceof Error ? cause.message : '상세를 불러오지 못했습니다.'
  } finally {
    if (id === requestId) loading.value = false
  }
}
watch(() => route.params.id, load, { immediate: true })
onBeforeUnmount(() => { requestId++; controller?.abort() })
</script>

<template>
  <section aria-labelledby="detail-title">
    <RouterLink to="/spaces">← 공간 목록</RouterLink>
    <div class="page-heading form-heading"><div><p class="eyebrow">공간 정보</p><h1 id="detail-title">{{ space?.name || '공간 상세' }}</h1></div><p class="timezone">시간 기준 <strong>Asia/Seoul</strong></p></div>
    <div v-if="loading" class="notice" role="status">공간 상세를 불러오는 중입니다…</div>
    <div v-else-if="error" class="notice error-state"><p role="alert">{{ error }}</p><button v-if="retryable" type="button" @click="load">다시 시도</button></div>
    <article v-else-if="space" class="detail-panel">
      <span class="capacity">{{ space.capacity }}명</span>
      <h2>공간 소개</h2><p class="description">{{ space.description || '등록된 설명이 없습니다.' }}</p>
      <dl><dt>공간 ID</dt><dd>{{ space.id }}</dd><dt>등록 시각 (Asia/Seoul)</dt><dd><time :datetime="space.createdAt">{{ time.format(new Date(space.createdAt)) }}</time></dd></dl>
    </article>
  </section>
</template>
