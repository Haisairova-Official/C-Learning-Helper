import { readonly, ref } from 'vue';

export type ColorScheme = 'light' | 'dark';
const scheme = ref<ColorScheme>('light');
export const colorScheme = readonly(scheme);

// Browsers expose the OS/browser appearance preference; no account preference is stored.
export function followSystemTheme(browser: Pick<Window, 'matchMedia'> = window, root = document.documentElement) {
  const apply = (dark: boolean) => {
    scheme.value = dark ? 'dark' : 'light';
    root.classList.toggle('dark', dark);
    root.style.colorScheme = scheme.value;
  };
  apply(false);
  let query: MediaQueryList;
  try {
    query = browser.matchMedia('(prefers-color-scheme: dark)');
    apply(query.matches === true);
  } catch {
    return () => {};
  }
  const onChange = () => apply(query.matches === true);
  if (typeof query.addEventListener === 'function') {
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }
  // Older WebKit exposes only the legacy listener API.
  if (typeof query.addListener === 'function') {
    query.addListener(onChange);
    return () => query.removeListener(onChange);
  }
  return () => {};
}
