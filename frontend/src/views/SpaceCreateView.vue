<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { createSpace, validateSpace, type SpaceErrors } from '../api/spaces'

const router = useRouter()
const name = ref('')
const description = ref('')
const capacity = ref('')
const errors = ref<SpaceErrors>({})
const error = ref('')
const submitting = ref(false)
let active = true
onBeforeUnmount(() => { active = false })

async function submit() {
  if (submitting.value) return
  errors.value = validateSpace(name.value, description.value, capacity.value)
  error.value = ''
  if (Object.keys(errors.value).length) {
    error.value = '입력값을 확인해 주세요.'
    return
  }
  submitting.value = true
  try {
    const space = await createSpace({ name: name.value, description: description.value === '' ? null : description.value, capacity: Number(capacity.value) })
    if (active) await router.push(`/spaces/${space.id}`)
  } catch (cause) {
    if (!active) return
    error.value = cause instanceof Error ? cause.message : '공간을 등록하지 못했습니다.'
    if (cause instanceof ApiError) {
      for (const field of cause.fieldErrors) {
        if (field.field === 'name' || field.field === 'description' || field.field === 'capacity') errors.value[field.field] = field.message
      }
    }
  } finally {
    if (active) submitting.value = false
  }
}
</script>

<template>
  <section aria-labelledby="create-title">
    <RouterLink to="/spaces">← 공간 목록</RouterLink>
    <div class="page-heading form-heading"><div><p class="eyebrow">새로운 공간</p><h1 id="create-title">공간 등록</h1><p class="lead">함께 사용할 공간의 정보를 입력하세요.</p></div></div>
    <form class="space-form" novalidate :aria-busy="submitting" @submit.prevent="submit">
      <p v-if="error" role="alert" class="field-error">{{ error }}</p>
      <fieldset :disabled="submitting">
        <div class="form-field">
          <label for="name">공간 이름 <span>(필수)</span></label>
          <input id="name" v-model="name" type="text" maxlength="100" required :aria-invalid="!!errors.name" :aria-describedby="errors.name ? 'name-error' : undefined">
          <p v-if="errors.name" id="name-error" class="field-error">{{ errors.name }}</p>
        </div>
        <div class="form-field">
          <label for="description">설명 <span>(선택 · 최대 2,000자)</span></label>
          <textarea id="description" v-model="description" rows="5" maxlength="2000" :aria-invalid="!!errors.description" :aria-describedby="errors.description ? 'description-error' : undefined" />
          <p v-if="errors.description" id="description-error" class="field-error">{{ errors.description }}</p>
        </div>
        <div class="form-field">
          <label for="capacity">수용 인원 <span>(필수)</span></label>
          <input id="capacity" v-model="capacity" type="text" inputmode="numeric" required :aria-invalid="!!errors.capacity" :aria-describedby="errors.capacity ? 'capacity-error' : undefined">
          <p v-if="errors.capacity" id="capacity-error" class="field-error">{{ errors.capacity }}</p>
        </div>
      </fieldset>
      <p v-if="submitting" role="status">공간을 등록하는 중입니다…</p>
      <p class="form-help">응답이 끊겼다면 목록에서 등록 여부를 확인한 후 다시 시도하세요.</p>
      <div class="form-actions"><RouterLink to="/spaces">목록으로</RouterLink><button type="submit" :disabled="submitting">{{ submitting ? '등록 중…' : '공간 등록' }}</button></div>
    </form>
  </section>
</template>
