import { webcrypto } from 'node:crypto';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { newClientId } from '../lib/id';

describe('局域网 HTTP 请求标识', () => {
  afterEach(() => vi.unstubAllGlobals());
  it('没有 randomUUID 时仍可生成符合后端契约的独立请求标识', () => {
    vi.stubGlobal('crypto', { getRandomValues: webcrypto.getRandomValues.bind(webcrypto) });
    const first = newClientId();
    const second = newClientId();
    expect(first).toMatch(/^[A-Za-z0-9-]{16,128}$/);
    expect(second).toMatch(/^[A-Za-z0-9-]{16,128}$/);
    expect(second).not.toBe(first);
  });
});
