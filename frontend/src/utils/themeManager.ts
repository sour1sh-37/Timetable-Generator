import { generateThemePalette, normalizeHex } from './colorUtils';

export type DisplayMode = 'light' | 'dark' | 'system';

export const STORAGE_KEY_ACCENT = 'sies_accent_color';
export const STORAGE_KEY_MODE = 'sies_display_mode';
export const DEFAULT_ACCENT = '#8B1E29';
export const DEFAULT_MODE: DisplayMode = 'light';

export interface ThemeSettings {
  accentColor: string;
  displayMode: DisplayMode;
  effectiveTheme: 'light' | 'dark';
}

/** Get user's saved accent color from localStorage or fallback */
export function getStoredAccentColor(): string {
  try {
    const saved = localStorage.getItem(STORAGE_KEY_ACCENT);
    if (saved) return normalizeHex(saved, DEFAULT_ACCENT);
  } catch (err) {
    console.warn('Could not read accent color from localStorage:', err);
  }
  return DEFAULT_ACCENT;
}

/** Get user's saved display mode from localStorage or fallback */
export function getStoredDisplayMode(): DisplayMode {
  try {
    const saved = localStorage.getItem(STORAGE_KEY_MODE);
    if (saved === 'light' || saved === 'dark' || saved === 'system') {
      return saved;
    }
  } catch (err) {
    console.warn('Could not read display mode from localStorage:', err);
  }
  return DEFAULT_MODE;
}

/** Resolve 'system' display mode to actual 'light' or 'dark' */
export function resolveEffectiveTheme(mode: DisplayMode): 'light' | 'dark' {
  if (mode === 'system') {
    if (typeof window !== 'undefined' && window.matchMedia) {
      return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    return 'light';
  }
  return mode;
}

/**
 * Apply theme to document element by updating CSS custom properties and attributes.
 * Persists preferences when requested.
 */
export function applyTheme(
  accentHex: string,
  mode: DisplayMode,
  persist = true
): ThemeSettings {
  const normHex = normalizeHex(accentHex, DEFAULT_ACCENT);
  const effectiveTheme = resolveEffectiveTheme(mode);
  const isDark = effectiveTheme === 'dark';

  if (typeof document !== 'undefined') {
    const root = document.documentElement;
    root.setAttribute('data-theme', effectiveTheme);

    const palette = generateThemePalette(normHex, isDark);
    root.style.setProperty('--color-brand', palette.brand);
    root.style.setProperty('--color-brand-hover', palette.brandHover);
    root.style.setProperty('--color-brand-active', palette.brandActive);
    root.style.setProperty('--color-brand-subtle', palette.brandSubtle);
    root.style.setProperty('--color-brand-border', palette.brandBorder);
    root.style.setProperty('--color-brand-text', palette.brandText);
    root.style.setProperty('--focus-ring', palette.focusRing);
  }

  if (persist) {
    try {
      localStorage.setItem(STORAGE_KEY_ACCENT, normHex);
      localStorage.setItem(STORAGE_KEY_MODE, mode);
    } catch (err) {
      console.warn('Could not save theme preferences to localStorage:', err);
    }
  }

  return {
    accentColor: normHex,
    displayMode: mode,
    effectiveTheme,
  };
}

/** Initialize theme from localStorage at application startup */
export function initTheme(): ThemeSettings {
  const accent = getStoredAccentColor();
  const mode = getStoredDisplayMode();
  return applyTheme(accent, mode, false);
}
