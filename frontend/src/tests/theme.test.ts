import { describe, expect, it, vi } from 'vitest';
import { colorScheme, followSystemTheme } from '../theme';

function fixture(dark = false, legacy = false) {
  const classes = new Set(['existing-class']);
  const root = {
    classList: { toggle: (name: string, enabled: boolean) => enabled ? classes.add(name) : classes.delete(name) },
    style: { colorScheme: '' },
  } as unknown as HTMLElement;
  const listeners = new Set<() => void>();
  const add = vi.fn((listener: () => void) => { listeners.add(listener); });
  const remove = vi.fn((listener: () => void) => { listeners.delete(listener); });
  const query = {
    matches: dark,
    ...(legacy ? { addListener: add, removeListener: remove } : {
      addEventListener: (_type: string, listener: () => void) => add(listener),
      removeEventListener: (_type: string, listener: () => void) => remove(listener),
    }),
  } as unknown as MediaQueryList;
  const browser = { matchMedia: vi.fn(() => query) };
  const change = (value: boolean) => {
    Object.assign(query, { matches: value });
    listeners.forEach(listener => listener());
  };
  return { browser, root, classes, add, remove, change };
}

describe('system color scheme', () => {
  it.each([true, false])('applies the initial preference before mounting (dark=%s)', dark => {
    const f = fixture(dark);
    const stop = followSystemTheme(f.browser, f.root);
    expect(f.browser.matchMedia).toHaveBeenCalledWith('(prefers-color-scheme: dark)');
    expect(colorScheme.value).toBe(dark ? 'dark' : 'light');
    expect(f.root.style.colorScheme).toBe(colorScheme.value);
    expect(f.classes.has('dark')).toBe(dark);
    expect(f.classes.has('existing-class')).toBe(true);
    stop();
  });

  it.each([false, true])('follows both changes and detaches listeners (legacy=%s)', legacy => {
    const f = fixture(false, legacy);
    const stop = followSystemTheme(f.browser, f.root);
    f.change(true);
    expect(colorScheme.value).toBe('dark');
    expect(f.classes.has('dark')).toBe(true);
    f.change(false);
    expect(colorScheme.value).toBe('light');
    expect(f.classes.has('dark')).toBe(false);
    expect(f.root.style.colorScheme).toBe('light');
    stop();
    expect(f.remove).toHaveBeenCalledWith(f.add.mock.calls[0]![0]);
    f.change(true);
    expect(colorScheme.value).toBe('light');
  });

  it.each(['missing', 'throws', 'unknown'] as const)('keeps the original light palette when detection is %s', kind => {
    const f = fixture(true);
    followSystemTheme(f.browser, f.root)();
    const browser = kind === 'missing' ? {} : { matchMedia: () => {
      if (kind === 'throws') throw new Error('unavailable');
      return { matches: false, media: 'not all' };
    } };
    expect(() => followSystemTheme(browser as unknown as Pick<Window, 'matchMedia'>, f.root)()).not.toThrow();
    expect(colorScheme.value).toBe('light');
    expect(f.classes.has('dark')).toBe(false);
    expect(f.root.style.colorScheme).toBe('light');
  });

  it('uses the detected initial scheme even if change listeners are unavailable', () => {
    const f = fixture();
    const browser = { matchMedia: () => ({ matches: true }) } as unknown as Pick<Window, 'matchMedia'>;
    expect(() => followSystemTheme(browser, f.root)()).not.toThrow();
    expect(colorScheme.value).toBe('dark');
  });
});
