<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AdminShell from './components/AdminShell.vue'
import ToastHost from './components/ToastHost.vue'

const route = useRoute()
// 登录页等无壳页面
const isBare = computed(() => route.meta?.bare === true)
</script>

<template>
  <AdminShell v-if="!isBare">
    <RouterView v-slot="{ Component }">
      <component :is="Component" :key="route.fullPath" />
    </RouterView>
  </AdminShell>
  <RouterView v-else />
  <ToastHost />
</template>
