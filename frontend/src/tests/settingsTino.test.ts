// @vitest-environment happy-dom
import { afterEach,beforeEach,describe,expect,it,vi } from 'vitest';
import { createApp,nextTick } from 'vue';
import ElementPlus from 'element-plus';
import type { Profile,ProfileUpdate } from '../api/types';
const mocks=vi.hoisted(()=>({profile:vi.fn(),saveProfile:vi.fn(),unlock:vi.fn(),leave:vi.fn()}));
vi.mock('vue-router',()=>({onBeforeRouteLeave:mocks.leave}));
vi.mock('../api',()=>({api:mocks,isMock:false}));
vi.mock('../api/http',()=>({post:mocks.unlock}));
vi.mock('../components/PasswordForm.vue',()=>({default:{render:()=>null}}));
import SettingsView from '../views/SettingsView.vue';
import { actor,clearSession } from '../auth';
let cleanup=()=>{};
let stored:Profile;
async function flush(){for(let i=0;i<12;i++){await Promise.resolve();await nextTick();}}
async function mount(){
 const host=document.createElement('div');document.body.append(host);const app=createApp(SettingsView).use(ElementPlus);app.mount(host);await flush();cleanup=()=>{app.unmount();host.remove();};
 const button=(text:string)=>[...host.querySelectorAll<HTMLButtonElement>('button')].find(b=>b.textContent?.trim()===text||b.textContent?.includes(text))!;
 return {host,button};
}
beforeEach(()=>{
 vi.resetAllMocks();clearSession();actor.value={id:'tino-check',role:'STUDENT'};
 stored={username:'test',displayName:'测试',nickname:'',bio:'',avatarUrl:null,aiReplyStyle:'GUIDED',updatedAt:'2026-11-15T00:00:00Z',classNames:[],studentNumber:null,tinoUnlocked:false};
 mocks.profile.mockImplementation(async()=>({...stored}));mocks.unlock.mockImplementation(async()=>{stored={...stored,tinoUnlocked:true};return {...stored};});
 mocks.saveProfile.mockImplementation(async(input:ProfileUpdate)=>{stored={...stored,...input};return {...stored};});
});
afterEach(()=>{cleanup();document.querySelectorAll('.el-message').forEach(n=>n.remove());});
describe('Tino settings full save flow',()=>{
 it('retains the selected style after unlock, saves TINO and preserves it after remount',async()=>{
  let view=await mount();
  const names:Record<string,string>={'1':'简洁','2':'引导','3':'详细'};
  for(const digit of '12332112323'){view.button(names[digit]!).click();await flush();}
  expect(mocks.unlock).toHaveBeenCalledOnce();
  view.button('小侦探Tino').click();await flush();
  expect(view.button('小侦探Tino').getAttribute('aria-checked')).toBe('true');
  view.button('简洁').click();await flush();expect(view.button('简洁').getAttribute('aria-checked')).toBe('true');
  view.button('小侦探Tino').click();await flush();
  await new Promise(resolve=>setTimeout(resolve,200));await flush();
  expect(mocks.saveProfile).toHaveBeenCalledWith({nickname:'',bio:'',aiReplyStyle:'TINO'});expect(stored.aiReplyStyle).toBe('TINO');
  cleanup();view=await mount();expect(view.button('小侦探Tino').getAttribute('aria-checked')).toBe('true');
  view.button('详细').click();await flush();expect(view.button('详细').getAttribute('aria-checked')).toBe('true');
  await new Promise(resolve=>setTimeout(resolve,200));await flush();expect(stored.aiReplyStyle).toBe('DETAILED');
 });
 it('flushes pending style changes before leaving and keeps unsaved profile text separate',async()=>{
  stored.tinoUnlocked=true;
  const view=await mount();
  const nickname=view.host.querySelector<HTMLInputElement>('#profile-nickname')!;
  nickname.value='尚未保存的昵称';nickname.dispatchEvent(new Event('input',{bubbles:true}));await flush();
  view.button('小侦探Tino').click();await flush();
  const leave=mocks.leave.mock.calls.at(-1)![0];expect(await leave()).toBe(true);
  expect(stored.aiReplyStyle).toBe('TINO');expect(stored.nickname).toBe('');expect(nickname.value).toBe('尚未保存的昵称');
 });
 it('keeps the requested style on save failure and blocks leaving until a retry succeeds',async()=>{
  stored.tinoUnlocked=true;const view=await mount();
  mocks.saveProfile.mockRejectedValueOnce(new Error('暂时无法连接'));
  view.button('小侦探Tino').click();await flush();
  const leave=mocks.leave.mock.calls.at(-1)![0];expect(await leave()).toBe(false);
  expect(view.button('小侦探Tino').getAttribute('aria-checked')).toBe('true');
  expect(await leave()).toBe(true);expect(stored.aiReplyStyle).toBe('TINO');
 });

});
