import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { request, resetCsrf } from '../api/http';
describe('真实 HTTP 客户端', () => {
  beforeEach(() => { resetCsrf(); vi.stubGlobal('window', new EventTarget()); });
  afterEach(() => vi.unstubAllGlobals());
  it('写操作携带同源 Session、服务端指定的 CSRF 头和幂等键', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf-token', parameterName: '_csrf' })).mockResolvedValueOnce(Response.json({ id: 's1' }, { status: 202 })); vi.stubGlobal('fetch', fetch);
    await request('/submissions', { method: 'POST', body: '{"sourceCode":"test"}', headers: { 'Idempotency-Key': 'test-key-123456789' } });
    expect(fetch).toHaveBeenCalledTimes(2); const [url, options] = fetch.mock.calls[1]!; expect(url).toBe('/api/submissions'); expect(options.credentials).toBe('same-origin'); expect(options.headers.get('X-CSRF-TOKEN')).toBe('csrf-token'); expect(options.headers.get('Idempotency-Key')).toBe('test-key-123456789');
  });
  it('上游失败会抛出真实错误，不自动重试或返回模拟结果', async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ code: 'UNAVAILABLE', message: '评测服务暂不可用', traceId: 'trace1' }, { status: 503 })); vi.stubGlobal('fetch', fetch);
    await expect(request('/submissions/s1')).rejects.toMatchObject({ code: 'UNAVAILABLE', status: 503, traceId: 'trace1' }); expect(fetch).toHaveBeenCalledTimes(1);
  });
  it('业务请求的 401 会通知应用重新登录', async () => {
    const listener = vi.fn(); window.addEventListener('cassist:session-expired', listener); vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ message: '请登录' }, { status: 401 })));
    await expect(request('/student/assignments')).rejects.toMatchObject({ status: 401 }); expect(listener).toHaveBeenCalledTimes(1);
  });
});
