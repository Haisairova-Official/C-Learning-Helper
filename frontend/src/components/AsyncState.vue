<script setup lang="ts">
defineProps<{ loading: boolean; error?: string; empty?: boolean; emptyText?: string }>();
defineEmits<{ retry: [] }>();
</script>
<template><el-skeleton v-if="loading" :rows="6" animated class="panel" /><div v-else-if="error" class="panel error-state" role="alert"><strong>暂时无法加载</strong><p>{{ error }}</p><el-button @click="$emit('retry')">重新加载</el-button></div><el-empty v-else-if="empty" :description="emptyText || '这里还没有内容'" /><slot v-else /></template>
