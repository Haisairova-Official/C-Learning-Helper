// @vitest-environment happy-dom
import { afterEach, describe, expect, it } from 'vitest';
import { createApp, h, nextTick, reactive } from 'vue';
import ReplyStylePicker from '../components/ReplyStylePicker.vue';
import { replyStyles } from '../lib/profile';
import { advanceTinoSequence, tinoSequence, tinoStyle } from '../lib/tino';
import type { AiReplyStyle } from '../api/types';
let cleanup=()=>{};
afterEach(()=>cleanup());
function mount(){
 const state=reactive({value:'GUIDED' as AiReplyStyle,unlocked:false,disabled:false});let sequence='';const selections:AiReplyStyle[]=[];
 const host=document.createElement('div');document.body.append(host);
 const app=createApp({render:()=>h(ReplyStylePicker,{modelValue:state.value,styles:state.unlocked?[...replyStyles,tinoStyle]:replyStyles,disabled:state.disabled,'onUpdate:modelValue':(value:AiReplyStyle)=>state.value=value,onSelect:(value:AiReplyStyle)=>{selections.push(value);if(!state.unlocked){sequence=advanceTinoSequence(sequence,value);if(sequence===tinoSequence)state.unlocked=true;}}})});app.mount(host);cleanup=()=>{app.unmount();host.remove();};
 const button=(value:string)=>[...host.querySelectorAll<HTMLButtonElement>('button')].find(b=>b.textContent?.includes(value))!;
 return {host,state,selections,button};
}
describe('reply style selection after Tino unlock',()=>{
 it('unlocks through repeated card clicks and allows clicking Tino title or description',async()=>{
  const {host,state,button}=mount();const names:Record<string,string>={'1':'简洁','2':'引导','3':'详细'};
  for(const digit of tinoSequence){button(names[digit]!).querySelector<HTMLElement>('strong')!.click();await nextTick();}
  expect(state.unlocked).toBe(true);
  button('小侦探Tino').querySelector<HTMLElement>('.style-copy>span')!.click();await nextTick();
  expect(state.value).toBe('TINO');expect(button('小侦探Tino').getAttribute('aria-checked')).toBe('true');
  expect(host.querySelectorAll('[aria-checked="true"]')).toHaveLength(1);
  button('简洁').click();await nextTick();expect(state.value).toBe('CONCISE');
  button('小侦探Tino').querySelector<HTMLElement>('strong')!.click();await nextTick();expect(state.value).toBe('TINO');
 });
 it('remains selectable after an asynchronous unlock completes',async()=>{
  const {state,button}=mount();state.disabled=true;state.unlocked=true;await nextTick();
  button('小侦探Tino').click();expect(state.value).toBe('GUIDED');
  state.disabled=false;await nextTick();button('小侦探Tino').click();await nextTick();expect(state.value).toBe('TINO');
 });
 it('supports keyboard selection of the unlocked style',async()=>{
  const {state,button}=mount();state.unlocked=true;state.value='DETAILED';await nextTick();
  button('详细').dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));await nextTick();
  expect(state.value).toBe('TINO');expect(document.activeElement).toBe(button('小侦探Tino'));
 });
});
