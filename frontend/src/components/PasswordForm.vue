<script setup lang="ts">
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import { post, resetCsrf } from '../api/http';
import { actor, clearSession } from '../auth';
const props=defineProps<{ userId?:string }>();
const router=useRouter();
const form=reactive({currentPassword:'',newPassword:'',confirm:''});
const busy=ref(false),error=ref('');
async function save(){
  if(busy.value)return;
  error.value='';
  if(form.newPassword.length<8 || new TextEncoder().encode(form.newPassword).length>72 || !/[a-zA-Z]/.test(form.newPassword) || !/[0-9]/.test(form.newPassword)){error.value='密码需包含字母和数字，至少 8 位且不超过 72 字节';return;}
  if(form.newPassword!==form.confirm){error.value='两次输入的新密码不一致';return;}
  if(!props.userId && !form.currentPassword){error.value='请输入当前密码';return;}
  busy.value=true;
  try{
    const self=!props.userId || props.userId===actor.value?.id;
    await post<void>(props.userId?`/admin/users/${encodeURIComponent(props.userId)}/password`:'/auth/password',props.userId?{newPassword:form.newPassword}:{currentPassword:form.currentPassword,newPassword:form.newPassword});
    form.currentPassword='';form.newPassword='';form.confirm='';
    ElMessage.success(self?'密码已修改，请重新登录':'密码已重置，该用户需要重新登录');
    if(self){resetCsrf();clearSession();await router.push('/login');}
  }catch(e){error.value=(e as Error).message;}finally{busy.value=false;}
}
</script>
<template><section><h2>{{userId?'重置密码':'修改密码'}}</h2><p class="muted">{{userId?'重置后，该用户的所有登录会话将退出。':'修改后需要重新登录。'}}</p><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-form label-position="top" :disabled="busy" @submit.prevent="save"><el-form-item v-if="!userId" label="当前密码"><el-input v-model="form.currentPassword" type="password" show-password autocomplete="current-password"/></el-form-item><el-form-item label="新密码"><el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" placeholder="至少 8 位，包含字母和数字"/></el-form-item><el-form-item label="确认新密码"><el-input v-model="form.confirm" type="password" show-password autocomplete="new-password"/></el-form-item><el-button type="primary" :loading="busy" @click="save">{{userId?'确认重置密码':'修改密码'}}</el-button></el-form></section></template>
