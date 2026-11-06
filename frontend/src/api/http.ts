export class ApiError extends Error {
  constructor(message: string, public status: number, public code: string, public traceId?: string) { super(message); }
}
type Csrf = { headerName: string; parameterName: string; token: string };
let csrf: Csrf | null = null;
let csrfRequest: Promise<Csrf> | null = null;
export function resetCsrf() { csrf = null; csrfRequest = null; }
async function getCsrf(): Promise<Csrf> {
  if (csrf) return csrf;
  if (!csrfRequest) csrfRequest = request<Csrf>('/auth/csrf').then(value => (csrf = value)).finally(() => { csrfRequest = null; });
  return csrfRequest;
}
export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  if (!['GET', 'HEAD', 'OPTIONS'].includes(options.method ?? 'GET')) {
    const token = await getCsrf(); headers.set(token.headerName, token.token);
  }
  let response: Response;
  try {
    const timeout = AbortSignal.timeout(15000);
    response = await fetch(`/api${path}`, { ...options, headers, credentials: 'same-origin', signal: options.signal ? AbortSignal.any([options.signal, timeout]) : timeout });
  } catch (error) {
    if (options.signal?.aborted) throw error;
    throw new ApiError('无法连接服务或请求超时，请稍后重试。', 0, 'NETWORK_ERROR');
  }
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    if (response.status === 401 && path !== '/auth/login' && path !== '/auth/me') window.dispatchEvent(new Event('cassist:session-expired'));
    if (response.status === 403) resetCsrf();
    throw new ApiError(data.message || `请求未完成（${response.status}）`, response.status, data.code || 'REQUEST_FAILED', data.traceId);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
export const post = <T>(path: string, body: unknown, key?: string) => request<T>(path, { method: 'POST', body: JSON.stringify(body), headers: key ? { 'Idempotency-Key': key } : undefined });
