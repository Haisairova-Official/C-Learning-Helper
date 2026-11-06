import { ref } from 'vue';
import { api } from './api';
import { post } from './api/http';
import type { Actor, Profile, ProfileUpdate } from './api/types';
import { clearDrafts } from './lib/drafts';
import { normalizeProfile } from './lib/profile';
export const actor = ref<Actor | null>(null);
export const profile = ref<Profile | null>(null);
export const profileError = ref('');
export const authError = ref('');
let initialized: Promise<void> | undefined;
let sessionGeneration = 0;

export function clearSession() {
  sessionGeneration++;
  actor.value = null;
  profile.value = null;
  profileError.value = '';
}

export async function refreshProfile(): Promise<Profile | undefined> {
  const generation = sessionGeneration; const owner = actor.value?.id;
  if (!owner) return undefined;
  profileError.value = '';
  try {
    const result = normalizeProfile(await api.profile());
    if (generation !== sessionGeneration || actor.value?.id !== owner) return undefined;
    profile.value = result;
    return result;
  } catch (error) {
    if (generation === sessionGeneration && actor.value?.id === owner) profileError.value = (error as Error).message;
    throw error;
  }
}

export function initAuth() {
  return initialized ??= api.me().then(async value => {
    sessionGeneration++; actor.value = value;
    await refreshProfile().catch(() => { /* Settings offers retry without preventing coursework. */ });
  }).catch(error => { if (error.status !== 401) authError.value = error.message; });
}

export async function login(username: string, password: string) {
  const value = await api.login(username, password);
  clearSession(); actor.value = value; authError.value = '';
  await refreshProfile().catch(() => { /* Keep the authenticated session usable. */ });
}

async function changeProfile(operation: () => Promise<Profile>): Promise<Profile | undefined> {
  const generation = sessionGeneration; const owner = actor.value?.id;
  if (!owner) return undefined;
  const result = normalizeProfile(await operation());
  if (generation !== sessionGeneration || actor.value?.id !== owner) return undefined;
  profile.value = result; profileError.value = '';
  return result;
}

export const unlockTino = (sequence:string) => changeProfile(() => post<Profile>('/profile/tino/unlock',{sequence}));
export const saveProfile = (input: ProfileUpdate) => changeProfile(() => api.saveProfile(input));
export const uploadAvatar = (file: File) => changeProfile(() => api.uploadAvatar(file));
export const removeAvatar = () => changeProfile(() => api.removeAvatar());

export async function logout() {
  const userId = actor.value?.id;
  await api.logout();
  if (userId) clearDrafts(userId);
  clearSession();
}
