<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <a class="skip-link" href="#main">본문으로 이동</a>
  <header class="site-header">
    <RouterLink to="/spaces" class="brand"><span class="brand-mark" aria-hidden="true">R</span>Resource Booking</RouterLink>
    <nav aria-label="주 메뉴"><RouterLink to="/spaces">공간 목록</RouterLink> <RouterLink to="/reservations">예약 목록</RouterLink> <RouterLink to="/reservations/new">예약 생성</RouterLink></nav>
  </header>
  <main id="main" tabindex="-1"><RouterView /></main>
  <footer>RESOURCE BOOKING SYSTEM <span>공간을 나누고, 가능성을 연결합니다.</span></footer>
</template>
