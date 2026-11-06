<script setup lang="ts">
import { computed, onMounted, ref, useId } from 'vue';
import type { Assignment } from '../api/types';
import { problemHint, progressLabel, progressTone } from '../lib/problemProgress';
const props = defineProps<{ assignment: Assignment; currentId: string; busy?: boolean }>();
defineEmits<{ navigate: [problemId: string] }>();
const index = computed(() => props.assignment.problems.findIndex(problem => problem.id === props.currentId));
const previous = computed(() => index.value > 0 ? props.assignment.problems[index.value - 1] : undefined);
const next = computed(() => index.value >= 0 ? props.assignment.problems[index.value + 1] : undefined);
const track = ref<HTMLOListElement>();
const directoryOpen = ref(false);
const directoryId = useId();
onMounted(() => { track.value?.querySelector('[aria-current="step"]')?.scrollIntoView({ block: 'nearest', inline: 'center' }); });
</script>

<template>
  <nav class="problem-navigation" aria-label="作业集题目导航">
    <div class="problem-navigation-top">
      <div class="problem-navigation-context">
        <span class="eyebrow">当前作业集</span>
        <strong>{{ assignment.title }}</strong>
      </div>
      <div class="problem-navigation-actions">
        <button class="adjacent-button" type="button" :disabled="busy || !previous" :title="previous?.title || '已经是第一题'" @click="previous && $emit('navigate', previous.id)">← 上一题</button>
        <span class="problem-position" aria-live="polite">{{ index >= 0 ? `第 ${index + 1} 题 / 共 ${assignment.problems.length} 题` : '题目不在当前作业集中' }}</span>
        <button class="adjacent-button" type="button" :disabled="busy || !next" :title="next?.title || '已经是最后一题'" @click="next && $emit('navigate', next.id)">下一题 →</button>
      </div>
    </div>
    <div class="problem-track-scroll">
      <ol ref="track" class="problem-track" aria-label="点击题号跳转">
        <li v-for="(p, i) in assignment.problems" :key="p.id" :style="{ '--step-delay': `${Math.min(i, 9) * 35}ms` }">
          <button type="button" class="problem-step" :class="[progressTone(p), { current: p.id === currentId }]"
            :data-progress="p.progress?.status || 'UNKNOWN'" :disabled="busy"
            :aria-current="p.id === currentId ? 'step' : undefined" :aria-label="problemHint(p, i)" :title="problemHint(p, i)"
            @click="$emit('navigate', p.id)">
            {{ i + 1 }}<span v-if="p.progress?.pending" class="pending-indicator" aria-hidden="true">·</span>
          </button>
        </li>
      </ol>
    </div>
    <div class="progress-legend" aria-label="题目状态说明">
      <span><i class="neutral"></i>未提交</span><span><i class="wrong"></i>全部错误</span>
      <span><i class="partial"></i>部分正确</span><span><i class="correct"></i>全部正确</span>
      <small>按历史最佳成绩显示</small>
    </div>
    <section class="problem-directory">
      <button type="button" class="directory-toggle" :aria-expanded="directoryOpen" :aria-controls="directoryId" @click="directoryOpen = !directoryOpen"><span class="directory-chevron" :class="{ expanded: directoryOpen }" aria-hidden="true">›</span>题目目录 <span class="directory-caption">查看题名与完成状态</span></button>
      <div :id="directoryId" class="directory-collapse" :class="{ expanded: directoryOpen }" :inert="!directoryOpen" :aria-hidden="!directoryOpen"><div class="directory-clip"><div class="directory-grid">
        <button v-for="(p, i) in assignment.problems" :key="p.id" type="button" class="directory-item"
          :class="{ selected: p.id === currentId }" :disabled="busy" :title="problemHint(p, i)"
          :aria-current="p.id === currentId ? 'step' : undefined" @click="$emit('navigate', p.id)">
          <span class="directory-number" :class="progressTone(p)">{{ i + 1 }}</span>
          <span class="directory-description"><strong>{{ p.title }}</strong><small>{{ progressLabel(p) }}<template v-if="p.progress?.bestScore != null"> · {{ p.progress.bestScore }} / {{ p.maxScore }} 分</template></small></span>
          <span v-if="p.id === currentId" class="current-label">当前题</span>
        </button>
      </div></div></div>
    </section>
  </nav>
</template>

<style scoped>
.problem-navigation { padding: 16px 20px; margin-bottom: 20px; border: 1px solid #e4e9ee; border-radius: 9px; background: #fff; min-width: 0; }
.problem-navigation-top { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.problem-navigation-context { min-width: 0; }
.problem-navigation-context .eyebrow { margin-bottom: 5px; }
.problem-navigation-context strong { display: block; font-size: 13px; font-weight: 500; overflow-wrap: anywhere; }
.problem-navigation-actions { display: flex; align-items: center; gap: 14px; flex-shrink: 0; }
.problem-position { font-size: 12px; color: #647787; white-space: nowrap; font-variant-numeric: tabular-nums; }
.adjacent-button { padding: 8px 12px; border: 1px solid #cbe1e1; border-radius: 5px; color: #187e83; background: #f5fbfb; font-size: 12px; white-space: nowrap; }
.adjacent-button:hover:not(:disabled) { background: #e6f4f3; border-color: #85beba; }
.adjacent-button:disabled { color: #a4afb7; border-color: #e7ecef; background: #fafbfc; cursor: not-allowed; }
.problem-track-scroll { overflow-x: auto; margin-top: 17px; padding: 2px 0; scrollbar-width: thin; }
.problem-track { display: flex; align-items: center; justify-content: center; gap: 20px; width: max-content; min-width: 100%; padding: 7px; margin: 0; list-style: none; }
.problem-track li { position: relative; width: 40px; height: 48px; flex: 0 0 40px; display: grid; place-items: center; }
.problem-track li:not(:last-child)::after { content: ''; position: absolute; top: 23px; left: 41px; width: 18px; height: 2px; border-radius: 2px; background: #dfe6eb; }
.problem-step { position: relative; display: grid; place-items: center; width: 34px; height: 22px; padding: 0; border: 1px solid; border-radius: 7px; font-size: 11px; font-weight: 600; font-variant-numeric: tabular-nums; transition: box-shadow .15s, transform .15s; }
.problem-step.current { width: 38px; height: 38px; border-radius: 50%; font-size: 15px; outline: 2px solid #496375; outline-offset: 3px; }
.problem-step:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 3px 7px #21394918; }
.problem-step:disabled, .directory-item:disabled { cursor: wait; opacity: .7; }
.neutral { background: #f6f8fa; border-color: #d4dce3; color: #758391; }
.wrong { background: #fde8e8; border-color: #e9a0a0; color: #b94040; }
.partial { background: #fff2cd; border-color: #ddba60; color: #946b09; }
.correct { background: #e1f3e8; border-color: #8ac8a0; color: #287744; }
.pending-indicator { position: absolute; right: -4px; top: -8px; font-size: 26px; line-height: 18px; color: #506f87; }
.progress-legend { display: flex; align-items: center; justify-content: center; gap: 15px; flex-wrap: wrap; margin: 7px 0 15px; font-size: 10px; color: #7b8995; }
.progress-legend span { display: inline-flex; align-items: center; gap: 5px; }
.progress-legend i { display: inline-block; width: 10px; height: 10px; border-width: 1px; border-style: solid; border-radius: 3px; }
.progress-legend small { font-size: 10px; color: #8a97a2; }
.problem-directory { border-top: 1px solid #edf0f3; padding-top: 12px; }
.problem-directory summary { cursor: pointer; color: #4f6878; font-size: 12px; }
.problem-directory summary span { color: #95a0a9; font-size: 10px; margin-left: 8px; }
.directory-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 8px; padding-top: 12px; max-height: 290px; overflow-y: auto; }
.directory-item { display: flex; align-items: center; gap: 10px; padding: 11px; min-width: 0; text-align: left; background: #fff; border: 1px solid #e7ecef; border-radius: 6px; }
.directory-item:hover:not(:disabled), .directory-item.selected { background: #f7fafb; border-color: #a9c5cc; }
.directory-number { display: grid; place-items: center; width: 26px; height: 26px; flex: 0 0 26px; border: 1px solid; border-radius: 7px; font-size: 11px; }
.directory-description { min-width: 0; }
.directory-description strong { display: block; font-size: 12px; font-weight: 500; color: #435869; overflow-wrap: anywhere; }
.directory-description small { display: block; font-size: 10px; color: #8795a0; margin-top: 4px; }
.current-label { font-size: 10px; color: #527e87; margin-left: auto; white-space: nowrap; }
.problem-navigation { animation: navigation-arrive .38s ease-out both; }
.problem-track li { animation: step-arrive .38s cubic-bezier(.2,.75,.25,1.15) both; animation-delay: var(--step-delay, 0ms); }
.problem-step { transition: background-color .35s ease, border-color .35s ease, color .35s ease, box-shadow .22s ease, transform .22s cubic-bezier(.2,.8,.3,1.3); }
.problem-step.current::before { content: ''; position: absolute; inset: -6px; border: 1px solid currentColor; border-radius: 50%; opacity: 0; pointer-events: none; animation: current-ripple 1.2s ease-out 2; }
.problem-step:active:not(:disabled) { transform: scale(.9); }
.adjacent-button, .directory-item { transition: transform .2s ease, background-color .25s, border-color .25s, box-shadow .25s; }
.adjacent-button:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 4px 9px #248a8a12; }
.adjacent-button:active:not(:disabled), .directory-item:active:not(:disabled) { transform: scale(.97); }
.directory-item:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 3px 9px #29434f0c; }
.directory-number { transition: background-color .35s, border-color .35s, color .35s; }
.pending-indicator { animation: pending-breathe 1.4s ease-in-out infinite; }
.directory-toggle { display: flex; align-items: center; gap: 7px; width: 100%; text-align: left; padding: 0; border: 0; background: transparent; color: #4f6878; font-size: 12px; }
.directory-caption { color: #95a0a9; font-size: 10px; margin-left: 5px; }
.directory-chevron { display: inline-block; font-size: 19px; line-height: 18px; transform: rotate(0); transition: transform .25s ease; }
.directory-chevron.expanded { transform: rotate(90deg); }
.directory-collapse { display: grid; grid-template-rows: 0fr; opacity: 0; transition: grid-template-rows .32s cubic-bezier(.2,.7,.3,1), opacity .22s ease; }
.directory-collapse.expanded { grid-template-rows: 1fr; opacity: 1; }
.directory-clip { min-height: 0; overflow: hidden; }
@keyframes navigation-arrive { from { opacity: 0; transform: translateY(9px); } to { opacity: 1; transform: translateY(0); } }
@keyframes step-arrive { from { opacity: 0; transform: translateY(5px) scale(.8); } to { opacity: 1; transform: translateY(0) scale(1); } }
@keyframes current-ripple { from { opacity: .35; transform: scale(.85); } to { opacity: 0; transform: scale(1.32); } }
@keyframes pending-breathe { 0%, 100% { opacity: .4; } 50% { opacity: 1; } }
@media (prefers-reduced-motion: reduce) { *, *::before, *::after { animation: none !important; transition: none !important; } }
@media (max-width: 1050px) { .problem-navigation-top { align-items: stretch; flex-direction: column; } .problem-navigation-actions { justify-content: space-between; } }
@media (max-width: 600px) { .problem-navigation { padding: 14px 12px; } .problem-navigation-actions { gap: 6px; } .adjacent-button { padding: 8px; } .problem-position { font-size: 11px; } .progress-legend { gap: 9px; } .directory-grid { grid-template-columns: 1fr; } }
</style>
