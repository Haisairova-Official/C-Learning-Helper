<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { request } from '../api/http';
import type { Assignment } from '../api/types';
import ProblemChat from '../components/ProblemChat.vue';
import AsyncState from '../components/AsyncState.vue';
const id=String(useRoute().params.assignmentId),assignment=ref<Assignment>(),selected=ref(''),loading=ref(true),error=ref('');
async function load(){loading.value=true;error.value='';try{assignment.value=await request<Assignment>(`/teacher/assignments/${encodeURIComponent(id)}`);selected.value=assignment.value.problems[0]?.id||'';}catch(e){error.value=(e as Error).message;}finally{loading.value=false;}}
onMounted(load);
</script>
<template><div class="page chat-management"><RouterLink to="/teacher/assignments" class="back-link">← 返回作业</RouterLink><div class="page-heading"><h1>{{assignment?.title||'讨论管理'}}</h1><el-select v-model="selected" placeholder="选择题目" style="width:320px"><el-option v-for="p in assignment?.problems||[]" :key="p.id" :value="p.id" :label="p.title"/></el-select></div><AsyncState :loading="loading" :error="error" @retry="load"><ProblemChat v-if="assignment&&selected" :key="selected" :assignment-id="id" :problem-id="selected" :enabled="!!assignment.chatEnabled"/></AsyncState></div></template>
<style scoped>.chat-management{max-width:960px}.page-heading{margin-top:15px}</style>
