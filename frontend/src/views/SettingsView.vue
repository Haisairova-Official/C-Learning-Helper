<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Camera, Check } from '@element-plus/icons-vue';
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
    const value=await unlockTino(sequence);
    if(value)ElMessage.success('小侦探Tino已解锁');
  } catch(e) {saveError.value=(e as Error).message;}
  finally {sequence='';unlocking.value=false;}
}
function fill(value: Profile) { form.nickname = value.nickname; form.bio = value.bio; form.aiReplyStyle = value.aiReplyStyle; }
async function load() {
  loading.value = true; error.value = '';
  try { const value = await refreshProfile(); if (value) fill(value); }
  catch (e) { error.value = (e as Error).message; }
  finally { loading.value = false; }
}
async function save() {
  if (busy.value || !changed.value) return;
  saveError.value = invalid.value; if (saveError.value) return;
  saving.value = true;
  try { const value = await saveProfile({ nickname: form.nickname.trim(), bio: form.bio, aiReplyStyle: form.aiReplyStyle }); if (value) { fill(value); ElMessage.success('资料与 AI 回复风格已保存'); } }
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
            <p class="muted">保存后，下次 AI 指导将使用所选风格。</p>
            <fieldset class="style-options" :disabled="busy"><legend class="visually-hidden">选择 AI 回复风格</legend>
              <label v-for="style in visibleStyles" :key="style.value" class="style-option" :class="{ selected: form.aiReplyStyle === style.value, tino: style.value === 'TINO' }"><input v-model="form.aiReplyStyle" type="radio" name="reply-style" :value="style.value" @click="trackStyle(style.value)" /><span><strong>{{ style.title }}<small v-if="style.value === 'GUIDED'">默认</small></strong><span>{{ style.description }}</span></span><el-icon v-if="form.aiReplyStyle === style.value" class="selection-mark"><Check /></el-icon></label>
            </fieldset>
            
          </section>
        </div>
        <div class="settings-footer"><div aria-live="polite"><p v-if="saveError || invalid" class="form-error" role="alert">{{ saveError || invalid }}</p><span v-else class="muted">{{ changed ? '有尚未保存的修改' : '当前资料已保存' }}</span></div><el-button type="primary" :loading="saving" :disabled="busy || !changed || !!invalid" @click="save">保存设置</el-button></div>
      </template>
    </AsyncState>
  </div>
</template>

<style scoped>
.style-option.tino{animation:tino-arrive .4s ease-out;border-color:#bbaf8b;background:#fffdf7}.style-option.tino.selected{border-color:#a68a47;background:#faf5e7}@keyframes tino-arrive{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:translateY(0)}}@media(prefers-reduced-motion:reduce){.style-option.tino{animation:none}}
.settings-page{max-width:1180px}.settings-grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:24px;align-items:start}.profile-card h2{margin-bottom:24px}.avatar-editor{display:flex;gap:18px;align-items:center}.profile-avatar{height:80px;width:80px;border-radius:24px;background:#e7f3f1;color:#458a87;display:grid;place-items:center;font-size:30px;flex:none;overflow:hidden}.profile-avatar img{width:100%;height:100%;object-fit:cover}.avatar-editor strong{font-size:15px;overflow-wrap:anywhere}.avatar-editor p{margin:4px 0 9px}.avatar-actions{display:flex;gap:8px}.avatar-actions .el-button{font-size:12px}.avatar-input{display:none}.field-note{display:block;font-size:11px;color:#89969e;margin:7px 0 0;line-height:1.7}.school-identity{border-top:1px solid #edf0f3;border-bottom:1px solid #edf0f3;margin:24px 0;padding:18px 0}.school-identity>div{display:flex;justify-content:space-between;gap:20px;font-size:12px;margin-bottom:10px}.school-identity span{color:#8a96a0;flex:none}.school-identity strong{font-weight:500;text-align:right;overflow-wrap:anywhere}.school-identity p{font-size:11px;color:#93a0a8;margin:14px 0 0;line-height:1.7}.settings-alert{margin-top:12px}.profile-card :deep(.el-form-item:last-child){margin-bottom:0}.reply-card .section-bar{margin-bottom:8px}.preference-tag{font-size:10px;color:#6d9697;background:#f0f7f7;padding:4px 8px;border-radius:4px}.style-options{padding:0;margin:20px 0;border:0;display:grid;gap:12px}.style-option{position:relative;display:flex;gap:10px;align-items:flex-start;padding:16px;border:1px solid #e3e9ed;border-radius:7px;cursor:pointer;transition:border-color .15s,background .15s}.style-option.selected{border-color:#83b8b4;background:#f4faf9}.style-option input{accent-color:#238d8e;margin:3px 0 0}.style-option>span{min-width:0}.style-option strong{display:block;font-size:13px;font-weight:600;color:#405e6b;padding-right:16px}.style-option strong small{font-size:9px;font-weight:400;margin-left:8px;color:#8a9da5}.style-option span>span{display:block;font-size:11px;color:#83949e;margin-top:6px;line-height:1.7}.selection-mark{position:absolute;right:15px;top:17px;color:#278b87}.style-options:disabled .style-option{cursor:wait;opacity:.7}.settings-footer{display:flex;justify-content:space-between;align-items:center;gap:20px;margin-top:22px}.settings-footer .form-error{font-size:12px;color:#c25c55;margin:0}.visually-hidden{position:absolute;clip:rect(0,0,0,0);width:1px;height:1px;overflow:hidden;white-space:nowrap}@media(max-width:1050px){.settings-grid{grid-template-columns:1fr}.reply-card{max-width:none}}@media(max-width:600px){.profile-card,.reply-card{padding:20px}.profile-avatar{width:64px;height:64px;border-radius:19px}.settings-footer{align-items:flex-start}}
</style>
