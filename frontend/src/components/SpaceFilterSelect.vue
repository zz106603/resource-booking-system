<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { listSpaces, type SpacePage } from '../api/spaces'
const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const result = ref<SpacePage | null>(null)
const loading = ref(false)
const error = ref('')
const selectedName = ref('')
let requestedPage = 0
let requestId = 0
let controller: AbortController | undefined
async function load(page = 0) {
  const id = ++requestId
  controller?.abort(); controller = new AbortController()
  requestedPage = page; loading.value = true; error.value = ''
  try { const value = await listSpaces(page, controller.signal); if (id === requestId) result.value = value }
  catch (cause) { if (id === requestId) error.value = cause instanceof Error ? cause.message : '공간을 불러오지 못했습니다.' }
  finally { if (id === requestId) loading.value = false }
}
function select(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  selectedName.value = result.value?.content.find(space => String(space.id) === value)?.name ?? selectedName.value
  emit('update:modelValue', value)
}
onMounted(() => load())
onBeforeUnmount(() => { requestId++; controller?.abort() })
</script>
<template>
  <label for="filter-space">공간</label>
  <select id="filter-space" :value="props.modelValue" :disabled="loading" @change="select">
    <option value="">전체 공간</option>
    <option v-if="modelValue && !result?.content.some(space => String(space.id) === modelValue)" :value="modelValue">{{ selectedName || `공간 ID ${modelValue}` }} (선택됨)</option>
    <option v-for="space in result?.content || []" :key="space.id" :value="String(space.id)">{{ space.name }} · ID {{ space.id }}</option>
  </select>
  <p v-if="loading" role="status">공간 선택지를 불러오는 중입니다…</p>
  <div v-if="error"><p role="alert" class="field-error">{{ error }}</p><button type="button" @click="load(requestedPage)">공간 다시 불러오기</button></div>
  <nav v-if="result && result.totalPages > 1" class="space-filter-pages" aria-label="공간 필터 페이지">
    <button type="button" :disabled="loading || result.page === 0" @click="load(result.page - 1)">이전 공간</button><span>{{ result.page + 1 }} / {{ result.totalPages }}</span>
    <button type="button" :disabled="loading || result.page + 1 >= result.totalPages || (result.page + 1) * 20 > 2147483647" @click="load(result.page + 1)">다음 공간</button>
  </nav>
</template>
