import { describe, expect, it, vi } from 'vitest';
import { poll } from '../lib/poll';
describe('异步结果轮询', () => {
  it('完成后停止查询，保留每个真实返回状态', async () => {
    const states = ['QUEUED', 'RUNNING', 'FINISHED']; const read = vi.fn(async () => states.shift()!); const update = vi.fn();
    await expect(poll(read, x => x === 'FINISHED', update, new AbortController().signal, 1)).resolves.toBe('FINISHED');
    expect(read).toHaveBeenCalledTimes(3); expect(update.mock.calls.map(x => x[0])).toEqual(['QUEUED', 'RUNNING', 'FINISHED']);
  });
  it('离开页面后不再把延迟响应写入视图', async () => {
    const controller = new AbortController(); let finish!: (value: string) => void; const read = () => new Promise<string>(resolve => { finish = resolve; }); const update = vi.fn();
    const waiting = poll(read, () => true, update, controller.signal); controller.abort(); finish('FINISHED');
    await expect(waiting).rejects.toMatchObject({ name: 'AbortError' }); expect(update).not.toHaveBeenCalled();
  });
  it('持续未完成会退出并允许用户稍后重查', async () => {
    const read = vi.fn(async () => 'QUEUED'); await expect(poll(read, () => false, () => {}, new AbortController().signal, 1, 2)).rejects.toThrow('等待结果超时'); expect(read).toHaveBeenCalledTimes(2);
  });
});
