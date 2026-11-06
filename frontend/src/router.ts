import { createRouter, createWebHistory } from 'vue-router';
import { actor, authError, clearSession, initAuth } from './auth';
const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/login', component: () => import('./views/LoginView.vue'), meta: { public: true, title: '登录' } },
  { path: '/', component: () => import('./views/AssignmentsView.vue'), meta: { title: '我的作业' } },
  { path: '/settings', component: () => import('./views/SettingsView.vue'), meta: { title: '账号设置' } },
  { path: '/assignments/:assignmentId/problems/:problemId', component: () => import('./views/WorkspaceView.vue'), meta: { title: '在线作答' } },
  { path: '/teacher/problems', component: () => import('./views/LibrariesView.vue'), meta: { manager: true, title: '题库管理' } },
  { path: '/teacher/problems/:libraryId', component: () => import('./views/ProblemsView.vue'), meta: { manager: true, title: '题目管理' } },
  { path: '/admin/users', component: () => import('./views/UsersView.vue'), meta: { admin: true, title: '用户管理' } },
  { path: '/ai', component: () => import('./views/AiView.vue'), meta: { title: 'AI 辅助' } },
  { path: '/teacher/assignments/:assignmentId/chat', component: () => import('./views/ChatManagementView.vue'), meta: { manager: true, title: '讨论管理' } },
  { path: '/teacher/import', component: () => import('./views/ImportView.vue'), meta: { manager: true, title: '题目导入' } },
  { path: '/teacher/assignments', component: () => import('./views/TeacherAssignmentsView.vue'), meta: { manager: true, title: '作业发布' } },
  { path: '/teacher/classes', component: () => import('./views/ClassesView.vue'), meta: { manager: true, title: '教学班级' } },
  { path: '/teacher/results', component: () => import('./views/ResultsView.vue'), meta: { manager: true, title: '学习情况' } },
  { path: '/:pathMatch(.*)*', redirect: '/' },
] });
router.beforeEach(async to => { await initAuth(); if (!to.meta.public && !actor.value) return { path: '/login', query: { redirect: to.fullPath } }; if (to.meta.admin && actor.value?.role !== 'ADMIN') return actor.value?.role === 'STUDENT' ? '/' : '/teacher/problems'; if (to.meta.manager && actor.value?.role === 'STUDENT') return '/'; if (to.path === '/login' && actor.value) return actor.value.role === 'STUDENT' ? '/' : '/teacher/problems'; });
window.addEventListener('cassist:session-expired', () => { clearSession(); authError.value = '登录已过期，请重新登录。'; void router.replace('/login'); });
export default router;
