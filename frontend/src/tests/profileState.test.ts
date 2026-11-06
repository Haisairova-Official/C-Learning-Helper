import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Profile } from '../api/types';
const mocks = vi.hoisted(() => ({ profile: vi.fn(), saveProfile: vi.fn(), logout: vi.fn(), clearDrafts: vi.fn(), unlockTino: vi.fn() }));
vi.mock('../api', () => ({ api: mocks }));
vi.mock('../api/http', () => ({ post:mocks.unlockTino }));
vi.mock('../lib/drafts', () => ({ clearDrafts: mocks.clearDrafts }));
import { actor, clearSession, logout, profile, refreshProfile, saveProfile, unlockTino } from '../auth';
const original: Profile = { username: 'student', displayName: '学校姓名', nickname: '', bio: '', avatarUrl: null, aiReplyStyle: 'GUIDED', updatedAt: '2026-11-06T00:00:00Z', classNames: [], studentNumber: null };

describe('个人资料的会话隔离', () => {
  beforeEach(() => { vi.resetAllMocks(); clearSession(); actor.value = { id: 'student-a', role: 'STUDENT' }; });
  it('同一账号重新登录后也不接收旧会话的迟到资料', async () => {
    let resolve!: (value: Profile) => void;
    mocks.profile.mockReturnValue(new Promise<Profile>(r => { resolve = r; }));
    const pending = refreshProfile(); clearSession(); actor.value = { id: 'student-a', role: 'STUDENT' };
    resolve({ ...original, nickname: '旧会话昵称' }); await pending;
    expect(profile.value).toBeNull();
  });
  it('解锁的迟到响应不会覆盖退出后建立的会话资料', async () => {
    let resolve!: (value:Profile) => void;
    mocks.unlockTino.mockReturnValue(new Promise<Profile>(r=>{resolve=r;}));
    const pending=unlockTino('12332112323');
    clearSession();actor.value={id:'student-b',role:'STUDENT'};profile.value={...original,username:'student-b'};
    resolve({...original,tinoUnlocked:true});
    expect(await pending).toBeUndefined();
    expect(profile.value.username).toBe('student-b');expect(profile.value.tinoUnlocked).not.toBe(true);
  });
  it('保存只更新资料，学校姓名和身份来自服务端；失败保留已保存状态', async () => {
    profile.value = { ...original }; mocks.saveProfile.mockResolvedValueOnce({ ...original, nickname: '新昵称', aiReplyStyle: 'CONCISE' });
    await saveProfile({ nickname: '新昵称', bio: '', aiReplyStyle: 'CONCISE' });
    expect(profile.value?.displayName).toBe('学校姓名'); expect(profile.value?.nickname).toBe('新昵称'); expect(actor.value?.id).toBe('student-a');
    mocks.saveProfile.mockRejectedValueOnce(new Error('网络错误'));
    await expect(saveProfile({ nickname: '未保存', bio: '', aiReplyStyle: 'DETAILED' })).rejects.toThrow('网络错误');
    expect(profile.value?.nickname).toBe('新昵称');
  });
  it('会话过期保留草稿，显式退出仅清理退出账号的草稿和资料', async () => {
    profile.value = { ...original }; clearSession(); expect(profile.value).toBeNull(); expect(mocks.clearDrafts).not.toHaveBeenCalled();
    actor.value = { id: 'student-b', role: 'STUDENT' }; profile.value = { ...original }; mocks.logout.mockResolvedValueOnce(undefined);
    await logout(); expect(mocks.clearDrafts).toHaveBeenCalledExactlyOnceWith('student-b'); expect(actor.value).toBeNull(); expect(profile.value).toBeNull();
  });
});
