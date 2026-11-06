import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { accountSubtitle, avatarSource, avatarValidation, normalizeProfile, profileValidation } from '../lib/profile';
import type { Profile } from '../api/types';
import { realApi } from '../api/real';
import { resetCsrf } from '../api/http';

describe('账号设置输入与真实传输', () => {
  it('学生使用真实班级学号，多班短显示与完整提示一致，不借用登录名', () => {
    const profile = normalizeProfile({ username: 'student', classNames: ['一班', '二班'], studentNumber: '20260001' } as Profile);
    expect(accountSubtitle('STUDENT', profile)).toBe('一班 等 2 个班级 · 20260001');
    expect(accountSubtitle('STUDENT', profile, true)).toBe('一班、二班 · 20260001');
    expect(accountSubtitle('STUDENT', { ...profile, classNames: [], studentNumber: null })).toBe('未加入班级 · 学号未录入');
    expect(accountSubtitle('TEACHER', { ...profile, username: 'teacher' })).toBe('教师 · teacher');
  });
  beforeEach(() => { resetCsrf(); vi.stubGlobal('window', new EventTarget()); });
  afterEach(() => vi.unstubAllGlobals());
  it('Unicode 昵称按字符数限制，简介保留换行', () => {
    expect(profileValidation({ nickname: '  ' + '🙂'.repeat(32) + '  ', bio: '第一行\n第二行', aiReplyStyle: 'GUIDED' })).toBe('');
    expect(profileValidation({ nickname: '🙂'.repeat(33), bio: '', aiReplyStyle: 'CONCISE' })).toContain('32');
    expect(profileValidation({ nickname: '', bio: '字'.repeat(501), aiReplyStyle: 'DETAILED' })).toContain('500');
  });
  it('提前拒绝空文件、SVG 和超过限制的图片', () => {
    expect(avatarValidation({ type: 'image/png', size: 512 * 1024 })).toBe('');
    expect(avatarValidation({ type: 'image/jpeg', size: 512 * 1024 + 1 })).toContain('512');
    expect(avatarValidation({ type: 'image/svg+xml', size: 100 })).toContain('PNG');
    expect(avatarValidation({ type: 'image/png', size: 0 })).toContain('为空');
  });
  it('头像只接受当前账号的同源地址，生产不接受模拟 Blob', () => {
    expect(avatarSource('/api/profile/avatar?v=123456')).toBe('/api/profile/avatar?v=123456');
    for (const source of ['https://example.com/a.png', '//example.com/a.png', 'data:image/png;base64,AA', '/api/users/other/avatar', 'blob:demo']) expect(avatarSource(source)).toBeUndefined();
    expect(avatarSource('blob:demo', true)).toBe('blob:demo');
  });
  it('头像上传由浏览器生成 multipart boundary，上传与删除均携带 CSRF', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf', parameterName: '_csrf' })).mockImplementation(() => Promise.resolve(Response.json({ avatarUrl: null })));
    vi.stubGlobal('fetch', fetch);
    await realApi.uploadAvatar(new File(['png'], 'avatar.png', { type: 'image/png' }));
    const [uploadUrl, upload] = fetch.mock.calls[1]!;
    expect(uploadUrl).toBe('/api/profile/avatar'); expect(upload.method).toBe('POST'); expect(upload.body).toBeInstanceOf(FormData);
    expect(upload.body.get('file').name).toBe('avatar.png'); expect(upload.headers.has('Content-Type')).toBe(false); expect(upload.headers.get('X-CSRF-TOKEN')).toBe('csrf');
    await realApi.removeAvatar(); const [, removal] = fetch.mock.calls[2]!;
    expect(removal.method).toBe('DELETE'); expect(removal.headers.get('X-CSRF-TOKEN')).toBe('csrf');
  });
});
