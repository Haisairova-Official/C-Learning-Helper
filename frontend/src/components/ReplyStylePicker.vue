<script setup lang="ts">
import { Check } from '@element-plus/icons-vue';
import type { AiReplyStyle } from '../api/types';
const props=defineProps<{modelValue:AiReplyStyle;styles:readonly {value:AiReplyStyle;title:string;description:string}[];disabled?:boolean}>();
const emit=defineEmits<{'update:modelValue':[AiReplyStyle];select:[AiReplyStyle]}>();
function select(style:AiReplyStyle){if(props.disabled)return;emit('update:modelValue',style);emit('select',style);}
function move(event:KeyboardEvent,index:number){
 if(props.disabled)return;
 let next=index;
 if(event.key==='ArrowRight'||event.key==='ArrowDown')next=(index+1)%props.styles.length;
 else if(event.key==='ArrowLeft'||event.key==='ArrowUp')next=(index+props.styles.length-1)%props.styles.length;
 else if(event.key==='Home')next=0;
 else if(event.key==='End')next=props.styles.length-1;
 else return;
 event.preventDefault();select(props.styles[next]!.value);
 const button=event.currentTarget as HTMLElement;
 (button.parentElement?.querySelectorAll('button')[next] as HTMLElement|undefined)?.focus();
}
</script>
<template><fieldset class="style-options" role="radiogroup" aria-label="选择 AI 回复风格" :disabled="disabled"><legend class="visually-hidden">选择 AI 回复风格</legend><button v-for="(style,index) in styles" :key="style.value" type="button" role="radio" class="style-option" :class="{selected:modelValue===style.value,tino:style.value==='TINO'}" :aria-checked="modelValue===style.value" :tabindex="modelValue===style.value?0:-1" :disabled="disabled" @click="select(style.value)" @keydown="move($event,index)"><span class="radio-dot" aria-hidden="true"/><span class="style-copy"><strong>{{style.title}}<small v-if="style.value==='GUIDED'">默认</small></strong><span>{{style.description}}</span></span><Check v-if="modelValue===style.value" class="selection-mark" aria-hidden="true"/></button></fieldset></template>
<style scoped>
.style-options{padding:0;margin:20px 0;border:0;display:grid;gap:12px;min-width:0}.style-option{position:relative;display:flex;width:100%;gap:10px;align-items:flex-start;text-align:left;font:inherit;padding:16px;border:1px solid var(--border,#e3e9ed);border-radius:7px;background:transparent;cursor:pointer;transition:border-color .15s,background .15s}.style-option.selected{border-color:var(--accent-border,#83b8b4);background:var(--accent-soft,#f4faf9)}.radio-dot{width:13px;height:13px;border:1px solid var(--text-muted,#83949e);border-radius:50%;margin-top:3px;flex:none}.selected .radio-dot{border:4px solid var(--accent,#238d8e)}.style-copy{min-width:0}.style-copy strong{display:block;font-size:13px;font-weight:600;color:var(--text-secondary,#405e6b);padding-right:16px}.style-copy strong small{font-size:9px;font-weight:400;margin-left:8px;color:var(--text-muted,#8a9da5)}.style-copy>span{display:block;font-size:11px;color:var(--text-muted,#83949e);margin-top:6px;line-height:1.7}.selection-mark{position:absolute;width:14px;height:14px;right:15px;top:17px;color:var(--accent,#278b87)}.style-option:disabled{cursor:wait;opacity:.7}.style-option.tino{animation:tino-arrive .4s ease-out;border-color:var(--warning-border,#bbaf8b);background:var(--warning-soft,#fffdf7)}.style-option.tino.selected{border-color:var(--warning-border,#a68a47);background:var(--warning-soft,#faf5e7)}.visually-hidden{position:absolute;clip:rect(0,0,0,0);width:1px;height:1px;overflow:hidden;white-space:nowrap}@keyframes tino-arrive{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:translateY(0)}}@media(prefers-reduced-motion:reduce){.style-option.tino{animation:none}}
</style>
