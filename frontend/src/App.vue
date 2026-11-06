<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Collection, Document, Upload, User, DataAnalysis, SwitchButton, Setting, ChatDotRound } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { actor, logout, profile } from './auth';
import { isMock } from './api';
import { label } from './lib/format';
import SidebarAi from './components/SidebarAi.vue';
import { workspaceAi } from './aiWorkspace';
import { accountSubtitle, avatarSource } from './lib/profile';
const route = useRoute(); const router = useRouter();
const manager = computed(() => actor.value && actor.value.role !== 'STUDENT');
const displayName = computed(() => profile.value?.nickname || profile.value?.displayName || label(actor.value?.role || ''));
const subtitle = computed(() => accountSubtitle(actor.value?.role, profile.value));
const fullSubtitle = computed(() => accountSubtitle(actor.value?.role, profile.value, true));
const avatar = computed(() => avatarSource(profile.value?.avatarUrl, isMock));
const avatarFailed = ref(false);
watch(avatar, () => { avatarFailed.value = false; });
const nav = computed(() => manager.value ? [{ to: '/teacher/problems', label: '题库管理', icon: Collection }, { to: '/teacher/import', label: '题目导入', icon: Upload }, { to: '/teacher/assignments', label: '作业发布', icon: Document }, { to: '/teacher/classes', label: '教学班级', icon: User }, { to: '/teacher/results', label: '学习情况', icon: DataAnalysis }, ...(actor.value?.role === 'ADMIN' ? [{ to: '/admin/users', label: '用户管理', icon: User }] : [])] : [{ to: '/', label: '我的作业', icon: Document }, { to: route.params.assignmentId && route.params.problemId ? `/ai?assignmentId=${encodeURIComponent(String(route.params.assignmentId))}&problemId=${encodeURIComponent(String(route.params.problemId))}` : route.path === '/ai' ? route.fullPath : '/ai', label: 'AI 辅助', icon: ChatDotRound }]);
async function signOut() { try { await logout(); await router.replace('/login'); } catch (e) { ElMessage.error((e as Error).message); } }
</script>
<template>
  <div v-if="route.path === '/login'" class="login-shell"><RouterView /></div>
  <div v-else class="app-shell" :class="{ 'has-ai-panel': workspaceAi.active }">
    <aside class="sidebar">
      <RouterLink class="brand" :to="manager ? '/teacher/problems' : '/'"><span class="brand-mark">C</span><span>C 课堂<small>程序设计教学空间</small></span></RouterLink>
      <div class="nav-caption">{{ manager ? '教学管理' : '学习空间' }}</div>
      <nav aria-label="主导航"><RouterLink v-for="item in nav" :key="item.to" :to="item.to" :class="{ active: route.path === item.to.split('?')[0] || (item.to === '/teacher/problems' && route.path.startsWith('/teacher/problems/')) }" :aria-label="item.label" :title="item.label"><el-icon><component :is="item.icon" /></el-icon>{{ item.label }}</RouterLink><RouterLink to="/settings" :class="{ active: route.path === '/settings' }" aria-label="账号设置" title="账号设置"><el-icon><Setting /></el-icon>账号设置</RouterLink></nav>
      
      <SidebarAi />
      <div class="account"><RouterLink class="account-profile" to="/settings" :aria-label="`${displayName}，账号设置`" :title="`${displayName} · ${fullSubtitle}，点击进入账号设置`"><span class="avatar"><img v-if="avatar && !avatarFailed" :src="avatar" alt="我的头像" @error="avatarFailed = true" /><template v-else>{{ [...displayName][0] || '我' }}</template></span><span class="account-name"><strong>{{ displayName }}</strong><small :class="{ 'student-identity': actor?.role === 'STUDENT' }">{{ subtitle }}</small></span></RouterLink><button aria-label="退出登录" class="icon-button" @click.stop="signOut"><el-icon><SwitchButton /></el-icon></button></div>
    </aside>
    <div class="main-shell">
      <header class="topbar"><span class="breadcrumb">C 语言程序设计 <span>/</span> <strong>{{ route.meta.title }}</strong></span><span v-if="isMock" class="mode-pill demo"><i></i>演示模式</span></header>
      <div v-if="isMock" class="demo-banner" role="status"><strong>演示模式</strong> · 使用模拟数据，代码不实际运行，AI 内容为示例。</div>
      <main><RouterView :key="route.fullPath" /></main>
    </div>
  </div>
</template>

<style scoped>
.has-ai-panel .sidebar{width:288px}.has-ai-panel .main-shell{margin-left:288px}.has-ai-panel .brand,.has-ai-panel .nav-caption,.has-ai-panel nav,.has-ai-panel .account{flex-shrink:0}.has-ai-panel .nav-caption{margin-top:24px}.has-ai-panel .sidebar{height:100dvh;overflow-y:auto}.has-ai-panel .account{margin-top:0}
@media(max-width:1050px){.has-ai-panel .sidebar{width:250px}.has-ai-panel .main-shell{margin-left:250px}}
@media(max-width:880px){.has-ai-panel .sidebar nav{margin-top:12px}.has-ai-panel .sidebar nav a{font-size:13px;justify-content:flex-start;gap:10px}}
@media(max-width:760px){.has-ai-panel .sidebar{position:static;width:100%;height:auto;max-height:none;overflow:visible;flex-direction:column;align-items:stretch;gap:0;padding:12px 16px}.has-ai-panel .main-shell{margin-left:0}.has-ai-panel .sidebar :deep(.sidebar-ai){max-height:340px;min-height:180px;margin:12px 0 0}.has-ai-panel .account{display:none}.has-ai-panel .sidebar nav{display:flex;flex-wrap:wrap;margin-top:0;flex:none}.has-ai-panel .sidebar nav a{padding:8px 12px}.has-ai-panel .nav-caption{display:none}.has-ai-panel .brand{display:none}}
</style>
