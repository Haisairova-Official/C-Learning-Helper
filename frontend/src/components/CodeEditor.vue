<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as monaco from 'monaco-editor/editor/editor.api';
import 'monaco-editor/languages/definitions/cpp/register';
import EditorWorker from 'monaco-editor/editor/editor.worker?worker';
import type { Language } from '../api/types';
import { languageLabel } from '../lib/problemCode';
const props = defineProps<{ modelValue: string; readOnly?: boolean; language?: Language }>();
const emit = defineEmits<{ 'update:modelValue': [string] }>();
const host = ref<HTMLElement>(); let editor: monaco.editor.IStandaloneCodeEditor | undefined;
onMounted(() => {
  (self as typeof self & { MonacoEnvironment?: monaco.Environment }).MonacoEnvironment = { getWorker: () => new EditorWorker() };
  editor = monaco.editor.create(host.value!, { value: props.modelValue, language: 'cpp', theme: 'vs', automaticLayout: true, minimap: { enabled: false }, fontSize: 14, lineHeight: 24, fontFamily: "'SFMono-Regular', Consolas, 'Liberation Mono', monospace", scrollBeyondLastLine: false, padding: { top: 16 }, tabSize: 4, readOnly: props.readOnly, ariaLabel: `${languageLabel(props.language || 'C17')} 代码编辑器`, wordWrap: 'on', fixedOverflowWidgets: true });
  editor.onDidChangeModelContent(() => emit('update:modelValue', editor!.getValue()));
});
watch(() => props.modelValue, value => { if (editor && editor.getValue() !== value) editor.setValue(value); });
onBeforeUnmount(() => { const model = editor?.getModel(); editor?.dispose(); model?.dispose(); });
</script>
<template><div ref="host" class="code-editor" /></template>
