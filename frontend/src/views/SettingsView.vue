<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Camera } from '@element-plus/icons-vue';
import ReplyStylePicker from '../components/ReplyStylePicker.vue';
import PasswordForm from '../components/PasswordForm.vue';
import AsyncState from '../components/AsyncState.vue';
import { isMock } from '../api';
import { advanceTinoSequence, tinoSequence, tinoStyle } from '../lib/tino';
import type { AiReplyStyle, Profile, ProfileUpdate } from '../api/types';
import { profile, refreshProfile, removeAvatar, saveProfile, unlockTino, uploadAvatar } from '../auth';
import { avatarSource, avatarValidation, profileValidation, replyStyles } from '../lib/profile';

const form = reactive<ProfileUpdate>({ nickname: '', bio: '', aiReplyStyle: 'GUIDED' });
const loading = ref(true); const error = ref(''); const saving = ref(false); const avatarBusy = ref(false);
const saveError = ref(''); const avatarError = ref(''); const avatarFailed = ref(false); const fileInput = ref<HTMLInputElement>();
const unlocking = ref(false);
const styleSaving = ref(false);
let wantedStyle: AiReplyStyle | undefined;
let styleTimer: ReturnType<typeof setTimeout> | undefined;
let styleTask: Promise<void> | undefined;
let unlockTask: Promise<Profile | undefined> | undefined;
function persistStyle(): Promise<void> {
  clearTimeout(styleTimer);
  if (styleTask) return styleTask.then(() => wantedStyle ? persistStyle() : undefined);
  styleTask = (async () => {
    styleSaving.value = true;
    try {
      while (wantedStyle) {
        const style = wantedStyle; wantedStyle = undefined;
        const saved = profile.value;
        if (!saved || saved.aiReplyStyle === style) continue;
        try {
          await saveProfile({ nickname: saved.nickname, bio: saved.bio, aiReplyStyle: style });
          saveError.value = '';
        } catch (e) {
          wantedStyle ??= form.aiReplyStyle;
          saveError.value = (e as Error).message;
          ElMessage.error('回复风格保存失败：' + saveError.value);
          throw e;
        }
      }
    } finally { styleSaving.value = false; }
  })().finally(() => { styleTask = undefined; });
  return styleTask;
}
async function chooseStyle(style: AiReplyStyle) {
  if (busy.value) return;
  form.aiReplyStyle = style;
  await trackStyle(style);
  wantedStyle = form.aiReplyStyle;
  clearTimeout(styleTimer);
  styleTimer = setTimeout(() => { void persistStyle().catch(() => {}); }, 150);
}
onBeforeRouteLeave(async () => {
  try { await unlockTask; wantedStyle ??= form.aiReplyStyle; await persistStyle(); return true; } catch { return false; }
});
onBeforeUnmount(() => { clearTimeout(styleTimer); });
let sequence = '';
const busy = computed(() => saving.value || avatarBusy.value || unlocking.value);
const visibleStyles = computed(() => profile.value?.tinoUnlocked ? [...replyStyles,tinoStyle] : replyStyles);
const avatar = computed(() => avatarSource(profile.value?.avatarUrl, isMock));
const name = computed(() => profile.value?.nickname || profile.value?.displayName || '我');
const changed = computed(() => !!profile.value && (form.nickname !== profile.value.nickname || form.bio !== profile.value.bio || form.aiReplyStyle !== profile.value.aiReplyStyle));
const invalid = computed(() => profileValidation(form,!!profile.value?.tinoUnlocked));
watch(avatar, () => { avatarFailed.value = false; });
async function trackStyle(style: AiReplyStyle) {
  if(profile.value?.tinoUnlocked || busy.value || isMock) return;
  sequence=advanceTinoSequence(sequence,style);
  if(sequence!==tinoSequence) return;
  unlocking.value=true; saveError.value='';
  try {
    await persistStyle();
    unlockTask=unlockTino(sequence);
    const value=await unlockTask;
    if(value)ElMessage.success('小侦探Tino已解锁');
  } catch(e) {saveError.value=(e as Error).message;}
  finally {sequence='';unlocking.value=false;unlockTask=undefined;}
}
function fill(value: Profile) { form.nickname = value.nickname; form.bio = value.bio; form.aiReplyStyle = value.aiReplyStyle; }
async function load() {
  loading.value = true; error.value = '';
  try { const value = await refreshProfile(); if (value) fill(value); }
  catch (e) { error.value = (e as Error).message; }
  finally { loading.value = false; }
}
async function save() {
  if (busy.value || styleSaving.value || !changed.value) return;
  saveError.value = invalid.value; if (saveError.value) return;
  saving.value = true;
  try { await persistStyle(); const value = await saveProfile({ nickname: form.nickname.trim(), bio: form.bio, aiReplyStyle: form.aiReplyStyle }); if (value) { fill(value); ElMessage.success('资料与 AI 回复风格已保存'); } }
  catch (e) { saveError.value = (e as Error).message; }
  finally { saving.value = false; }
}
async function selectAvatar(event: Event) {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]; input.value = '';
  if (!file || busy.value) return;
  avatarError.value = avatarValidation(file); if (avatarError.value) return;
  avatarBusy.value = true;
  try { if (await uploadAvatar(file)) ElMessage.success('头像已更新'); }
  catch (e) { avatarError.value = (e as Error).message; }
  finally { avatarBusy.value = false; }
}
async function remove() {
  if (busy.value) return;
  avatarBusy.value = true; avatarError.value = '';
  try { if (await removeAvatar()) ElMessage.success('头像已移除'); }
  catch (e) { avatarError.value = (e as Error).message; }
  finally { avatarBusy.value = false; }
}
onMounted(load);
</script>

<template>
  <div class="page settings-page">
    <div class="page-heading"><div><h1>账号设置</h1></div></div>
    <AsyncState :loading="loading" :error="error" @retry="load">
      <template v-if="profile">
        <div class="settings-grid">
          <section class="panel profile-card" aria-labelledby="profile-title">
            <h2 id="profile-title">个人资料</h2>
            <div class="avatar-editor">
              <span class="profile-avatar"><img v-if="avatar && !avatarFailed" :src="avatar" alt="当前头像" @error="avatarFailed = true" /><template v-else>{{ [...name][0] }}</template></span>
              <div><strong>{{ name }}</strong><p class="muted">头像更改会立即保存</p><div class="avatar-actions"><el-button :icon="Camera" :loading="avatarBusy" :disabled="busy" @click="fileInput?.click()">更换头像</el-button><el-button v-if="profile.avatarUrl" link type="info" :disabled="busy" @click="remove">移除</el-button></div></div>
            </div>
            <input ref="fileInput" class="avatar-input" type="file" accept="image/png,image/jpeg" aria-label="选择头像图片" :disabled="busy" @change="selectAvatar" />
            <p class="field-note">PNG / JPEG，最多 512 KiB，宽高均不超过 4096 像素。</p>
            <el-alert v-if="avatarError || avatarFailed" class="settings-alert" :title="avatarError || '头像暂时未能加载，可以重新上传或稍后刷新。'" type="error" :closable="false" show-icon />
            <div class="school-identity"><div><span>登录账号</span><strong>{{ profile.username }}</strong></div><div><span>学校姓名</span><strong>{{ profile.displayName }}</strong></div><p>如需更正学校姓名，请联系管理员。</p></div>
            <el-form label-position="top" :disabled="busy" @submit.prevent="save">
              <el-form-item label="昵称" for="profile-nickname"><el-input id="profile-nickname" v-model="form.nickname" :placeholder="profile.displayName || '填写昵称'" autocomplete="nickname" /><span class="field-note">留空时显示学校姓名 · {{ [...form.nickname.trim()].length }} / 32</span></el-form-item>
              <el-form-item label="个人简介" for="profile-bio"><el-input id="profile-bio" v-model="form.bio" type="textarea" :rows="4" resize="vertical" placeholder="填写个人简介" /><span class="field-note">{{ [...form.bio].length }} / 500</span></el-form-item>
            </el-form>
          </section>
          <section class="panel reply-card" aria-labelledby="reply-title">
            <div class="section-bar"><h2 id="reply-title">AI 回复风格</h2><span class="preference-tag">学习偏好</span></div>
            <p class="muted">点击风格即保存，下次 AI 指导将使用所选风格。</p>
            <ReplyStylePicker :model-value="form.aiReplyStyle" :styles="visibleStyles" :disabled="busy" @select="chooseStyle"/><p class="muted" role="status">{{ styleSaving ? '正在保存回复风格…' : wantedStyle ? '回复风格等待保存' : '回复风格已保存' }}</p>
            
          </section>
        </div>
        <PasswordForm v-if="!isMock" class="panel mt"/>
        <div class="settings-footer"><div aria-live="polite"><p v-if="saveError || invalid" class="form-error" role="alert">{{ saveError || invalid }}</p><span v-else class="muted">{{ changed ? '有尚未保存的修改' : '当前资料已保存' }}</span></div><el-button type="primary" :loading="saving" :disabled="busy || styleSaving || !changed || !!invalid" @click="save">保存设置</el-button></div>
      </template>
    </AsyncState>
  </div>
</template>

<style scoped>
.settings-page{max-width:1180px}.settings-grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:24px;align-items:start}.profile-card h2{margin-bottom:24px}.avatar-editor{display:flex;gap:18px;align-items:center}.profile-avatar{height:80px;width:80px;border-radius:24px;background:var(--accent-soft, #e7f3f1);color:var(--accent, #458a87);display:grid;place-items:center;font-size:30px;flex:none;overflow:hidden}.profile-avatar img{width:100%;height:100%;object-fit:cover}.avatar-editor strong{font-size:15px;overflow-wrap:anywhere}.avatar-editor p{margin:4px 0 9px}.avatar-actions{display:flex;gap:8px}.avatar-actions .el-button{font-size:12px}.avatar-input{display:none}.field-note{display:block;font-size:11px;color:var(--text-muted, #89969e);margin:7px 0 0;line-height:1.7}.school-identity{border-top:1px solid var(--border, #edf0f3);border-bottom:1px solid var(--border, #edf0f3);margin:24px 0;padding:18px 0}.school-identity>div{display:flex;justify-content:space-between;gap:20px;font-size:12px;margin-bottom:10px}.school-identity span{color:var(--text-muted, #8a96a0);flex:none}.school-identity strong{font-weight:500;text-align:right;overflow-wrap:anywhere}.school-identity p{font-size:11px;color:var(--text-muted, #93a0a8);margin:14px 0 0;line-height:1.7}.settings-alert{margin-top:12px}.profile-card :deep(.el-form-item:last-child){margin-bottom:0}.reply-card .section-bar{margin-bottom:8px}.preference-tag{font-size:10px;color:var(--accent, #6d9697);background:var(--accent-soft, #f0f7f7);padding:4px 8px;border-radius:4px}.settings-footer{display:flex;justify-content:space-between;align-items:center;gap:20px;margin-top:22px}.settings-footer .form-error{font-size:12px;color:var(--danger-text, #c25c55);margin:0}.visually-hidden{position:absolute;clip:rect(0,0,0,0);width:1px;height:1px;overflow:hidden;white-space:nowrap}@media(max-width:1050px){.settings-grid{grid-template-columns:1fr}.reply-card{max-width:none}}@media(max-width:600px){.profile-card,.reply-card{padding:20px}.profile-avatar{width:64px;height:64px;border-radius:19px}.settings-footer{align-items:flex-start}}
</style>
