<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { api } from '../api';
import type { Assignment } from '../api/types';
import { dateTime } from '../lib/format';
import AsyncState from '../components/AsyncState.vue';
const rows = ref<Assignment[]>([]); const loading = ref(true); const error = ref(''); const search = ref('');
const visible = computed(() => rows.value.filter(x => x.title.includes(search.value)));
const total = computed(() => rows.value.reduce((n, x) => n + x.problems.length, 0));
const completed = computed(() => rows.value.reduce((n, x) => n + x.completedCount, 0));
async function load() { loading.value = true; error.value = ''; try { rows.value = (await api.assignments()).items; } catch (e) { error.value = (e as Error).message; } finally { loading.value = false; } }
const state = (a: Assignment) => Date.now() < +new Date(a.opensAt) ? '尚未开放' : Date.now() > +new Date(a.closesAt) ? '已截止' : '进行中';
onMounted(load);
</script>
<template><div class="page"><div class="page-heading"><div><h1>我的作业</h1></div><span class="course-badge">C 语言程序设计</span></div><div class="stats-strip"><div><span>本期作业</span><strong>{{ rows.length }}<small>份</small></strong></div><div><span>练习题目</span><strong>{{ total }}<small>题</small></strong></div><div><span>已完成</span><strong>{{ completed }}<small>题</small></strong></div><div class="stats-note"><span class="small-dot"></span><p>允许多次提交<br><small>每题计最高得分</small></p></div></div><div class="section-bar"><h2>课程作业 <span>{{ visible.length }}</span></h2><el-input v-model="search" placeholder="搜索作业名称" clearable aria-label="搜索作业" class="search-input" /></div><AsyncState :loading="loading" :error="error" :empty="!visible.length" empty-text="暂无符合条件的作业。" @retry="load"><article v-for="a in visible" :key="a.id" class="assignment-card"><div class="assignment-head"><div class="assignment-icon">C</div><div><div class="inline-title"><h2>{{ a.title }}</h2><el-tag :type="state(a) === '进行中' ? 'success' : 'info'" effect="plain">{{ state(a) }}</el-tag></div><p>{{ a.className }} <span class="separator">·</span> {{ a.problems.length }} 道题目</p></div><div class="assignment-deadline"><span>截止时间 · 北京时间</span><strong>{{ dateTime(a.closesAt) }}</strong></div></div><div class="problem-list"><div v-for="(p, i) in a.problems" :key="p.id" class="problem-row"><span class="problem-number">{{ String(i + 1).padStart(2, '0') }}</span><strong>{{ p.title }}</strong><span class="muted">{{ p.maxScore }} 分</span><RouterLink :to="`/assignments/${a.id}/problems/${p.id}`" class="row-link">{{ state(a) === '进行中' ? '开始作答' : '查看题目' }} <span>↗</span></RouterLink></div></div><footer><span>最高得分 <strong>{{ a.score }} / {{ a.maxScore }}</strong></span><span>{{ a.completedCount }} / {{ a.problems.length }} 题已完成</span></footer></article></AsyncState></div></template>
