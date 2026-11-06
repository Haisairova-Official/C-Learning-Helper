export async function poll<T>(read: (signal: AbortSignal) => Promise<T>, done: (value: T) => boolean, update: (value: T) => void, signal: AbortSignal, interval = 1000, maxAttempts = 120): Promise<T> {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    signal.throwIfAborted();
    const value = await read(signal);
    signal.throwIfAborted();
    update(value);
    if (done(value)) return value;
    await new Promise<void>((resolve, reject) => {
      const abort = () => { clearTimeout(timer); signal.removeEventListener('abort', abort); reject(new DOMException('Aborted', 'AbortError')); };
      const timer = setTimeout(() => { signal.removeEventListener('abort', abort); resolve(); }, interval);
      signal.addEventListener('abort', abort, { once: true });
    });
  }
  throw new Error('等待结果超时，请稍后重新查询。');
}
