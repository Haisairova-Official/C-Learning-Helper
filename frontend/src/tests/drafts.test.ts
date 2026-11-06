import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearDrafts, readDraft, saveDraft } from '../lib/drafts';
describe('共享设备代码草稿', () => {
  beforeEach(() => {
    const storage: Record<string, unknown> = {};
    Object.defineProperties(storage, { getItem: { value: (key: string) => storage[key] ?? null }, setItem: { value: (key: string, value: string) => { storage[key] = value; } }, removeItem: { value: (key: string) => { delete storage[key]; } } }); vi.stubGlobal('sessionStorage', storage);
  });
  afterEach(() => vi.unstubAllGlobals());
  it('相同题目下不同用户、作业和版本的草稿不会混用', () => {
    saveDraft('u1', 'a1', 'v1', 'student1'); saveDraft('u2', 'a1', 'v1', 'student2'); saveDraft('u1', 'a2', 'v1', 'other-assignment'); saveDraft('u1', 'a1', 'v2', 'new-version');
    expect(readDraft('u1', 'a1', 'v1')).toBe('student1'); expect(readDraft('u2', 'a1', 'v1')).toBe('student2'); expect(readDraft('u1', 'a2', 'v1')).toBe('other-assignment'); expect(readDraft('u1', 'a1', 'v2')).toBe('new-version');
  });
  it('退出时只清理当前用户草稿', () => { saveDraft('u1', 'a1', 'v1', 'one'); saveDraft('u2', 'a1', 'v1', 'two'); clearDrafts('u1'); expect(readDraft('u1', 'a1', 'v1')).toBeNull(); expect(readDraft('u2', 'a1', 'v1')).toBe('two'); });
  it('浏览器禁用存储时保留编辑能力并返回保存失败', () => { vi.stubGlobal('sessionStorage', { setItem() { throw new Error('quota'); }, getItem() { throw new Error('blocked'); } }); expect(saveDraft('u', 'a', 'v', 'code')).toBe(false); expect(readDraft('u', 'a', 'v')).toBeNull(); });
});
