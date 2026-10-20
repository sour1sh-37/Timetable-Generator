/**
 * High-precision Color Utilities for the SIES Custom Theme System.
 * Supports HSV <-> RGB <-> HEX conversions, HEX validation, and semantic theme derivation.
 */

export interface HsvColor {
  h: number; // 0 - 360
  s: number; // 0 - 1
  v: number; // 0 - 1
}

export interface RgbColor {
  r: number; // 0 - 255
  g: number; // 0 - 255
  b: number; // 0 - 255
}

export interface ThemePalette {
  brand: string;
  brandHover: string;
  brandActive: string;
  brandSubtle: string;
  brandBorder: string;
  brandText: string;
  focusRing: string;
}

/** Clamp a number between min and max */
export function clamp(val: number, min: number, max: number): number {
  return Math.min(Math.max(val, min), max);
}

/** Validate whether a string is a valid 3 or 6 digit hex color */
export function isValidHex(hex: string): boolean {
  if (!hex) return false;
  const clean = hex.startsWith('#') ? hex.slice(1) : hex;
  return /^[0-9A-Fa-f]{3}$|^[0-9A-Fa-f]{6}$/.test(clean);
}

/** Normalize hex string to standard 6-digit uppercase format with '#' */
export function normalizeHex(hex: string, fallback = '#8B1E29'): string {
  if (!isValidHex(hex)) return fallback;
  let clean = hex.startsWith('#') ? hex.slice(1) : hex;
  if (clean.length === 3) {
    clean = clean.split('').map((c) => c + c).join('');
  }
  return `#${clean.toUpperCase()}`;
}

/** Convert HEX string to RGB */
export function hexToRgb(hex: string): RgbColor {
  const norm = normalizeHex(hex);
  const clean = norm.slice(1);
  const r = parseInt(clean.substring(0, 2), 16);
  const g = parseInt(clean.substring(2, 4), 16);
  const b = parseInt(clean.substring(4, 6), 16);
  return { r, g, b };
}

/** Convert RGB to HEX */
export function rgbToHex(r: number, g: number, b: number): string {
  const toHex = (n: number) => {
    const clamped = clamp(Math.round(n), 0, 255);
    return clamped.toString(16).padStart(2, '0').toUpperCase();
  };
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

/** Convert RGB to HSV */
export function rgbToHsv(r: number, g: number, b: number): HsvColor {
  const rNorm = clamp(r, 0, 255) / 255;
  const gNorm = clamp(g, 0, 255) / 255;
  const bNorm = clamp(b, 0, 255) / 255;

  const max = Math.max(rNorm, gNorm, bNorm);
  const min = Math.min(rNorm, gNorm, bNorm);
  const delta = max - min;

  let h = 0;
  if (delta > 0) {
    if (max === rNorm) {
      h = ((gNorm - bNorm) / delta) % 6;
    } else if (max === gNorm) {
      h = (bNorm - rNorm) / delta + 2;
    } else {
      h = (rNorm - gNorm) / delta + 4;
    }
    h = h * 60;
    if (h < 0) h += 360;
  }

  const s = max === 0 ? 0 : delta / max;
  const v = max;

  return { h, s, v };
}

/** Convert HSV to RGB */
export function hsvToRgb(h: number, s: number, v: number): RgbColor {
  const hClamped = (clamp(h, 0, 360) % 360 + 360) % 360;
  const sClamped = clamp(s, 0, 1);
  const vClamped = clamp(v, 0, 1);

  const c = vClamped * sClamped;
  const x = c * (1 - Math.abs(((hClamped / 60) % 2) - 1));
  const m = vClamped - c;

  let rPrime = 0;
  let gPrime = 0;
  let bPrime = 0;

  if (hClamped < 60) {
    rPrime = c; gPrime = x; bPrime = 0;
  } else if (hClamped < 120) {
    rPrime = x; gPrime = c; bPrime = 0;
  } else if (hClamped < 180) {
    rPrime = 0; gPrime = c; bPrime = x;
  } else if (hClamped < 240) {
    rPrime = 0; gPrime = x; bPrime = c;
  } else if (hClamped < 300) {
    rPrime = x; gPrime = 0; bPrime = c;
  } else {
    rPrime = c; gPrime = 0; bPrime = x;
  }

  return {
    r: Math.round((rPrime + m) * 255),
    g: Math.round((gPrime + m) * 255),
    b: Math.round((bPrime + m) * 255),
  };
}

/** Convert HEX directly to HSV */
export function hexToHsv(hex: string): HsvColor {
  const rgb = hexToRgb(hex);
  return rgbToHsv(rgb.r, rgb.g, rgb.b);
}

/** Convert HSV directly to HEX */
export function hsvToHex(h: number, s: number, v: number): string {
  const rgb = hsvToRgb(h, s, v);
  return rgbToHex(rgb.r, rgb.g, rgb.b);
}

/** Compute relative luminance for accessibility contrast */
export function getLuminance(r: number, g: number, b: number): number {
  const [rs, gs, bs] = [r, g, b].map((val) => {
    const s = val / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
}

/** Generate a complete semantic palette from any chosen HEX accent */
export function generateThemePalette(hex: string, isDark: boolean): ThemePalette {
  const norm = normalizeHex(hex);
  const { r, g, b } = hexToRgb(norm);
  const hsv = rgbToHsv(r, g, b);

  // In dark mode, slightly boost brightness if too dark for contrast
  let adjustedV = hsv.v;
  let adjustedS = hsv.s;
  if (isDark) {
    adjustedV = Math.max(hsv.v, 0.65);
    adjustedS = Math.min(hsv.s, 0.85);
  }

  const baseColor = isDark && (adjustedV !== hsv.v || adjustedS !== hsv.s)
    ? hsvToHex(hsv.h, adjustedS, adjustedV)
    : norm;

  const baseRgb = hexToRgb(baseColor);

  // Derive Hover & Active shades
  const hoverHsv = {
    h: hsv.h,
    s: isDark ? Math.min(1, adjustedS * 0.95) : Math.min(1, hsv.s * 1.05),
    v: isDark ? Math.min(1, adjustedV * 1.1) : Math.max(0, hsv.v * 0.85),
  };
  const activeHsv = {
    h: hsv.h,
    s: isDark ? Math.min(1, adjustedS * 0.9) : Math.min(1, hsv.s * 1.1),
    v: isDark ? Math.max(0, adjustedV * 0.9) : Math.max(0, hsv.v * 0.75),
  };

  const brandHover = hsvToHex(hoverHsv.h, hoverHsv.s, hoverHsv.v);
  const brandActive = hsvToHex(activeHsv.h, activeHsv.s, activeHsv.v);

  // Background tints with alpha
  const brandSubtle = isDark
    ? `rgba(${baseRgb.r}, ${baseRgb.g}, ${baseRgb.b}, 0.18)`
    : `rgba(${baseRgb.r}, ${baseRgb.g}, ${baseRgb.b}, 0.09)`;

  const brandBorder = isDark
    ? `rgba(${baseRgb.r}, ${baseRgb.g}, ${baseRgb.b}, 0.38)`
    : `rgba(${baseRgb.r}, ${baseRgb.g}, ${baseRgb.b}, 0.28)`;

  // High contrast text on primary buttons
  const lum = getLuminance(baseRgb.r, baseRgb.g, baseRgb.b);
  const brandText = lum > 0.45 ? '#0f172a' : '#ffffff';

  const focusRing = isDark
    ? `0 0 0 2px #0b0f19, 0 0 0 4px ${baseColor}`
    : `0 0 0 2px #ffffff, 0 0 0 4px ${baseColor}`;

  return {
    brand: baseColor,
    brandHover,
    brandActive,
    brandSubtle,
    brandBorder,
    brandText,
    focusRing,
  };
}

/** Popular curated institutional presets */
export const ACCENT_PRESETS = [
  { name: 'SIES Crimson', hex: '#8B1E29', description: 'Institutional Burgundy (SIES Standard)' },
  { name: 'Royal Blue', hex: '#2563EB', description: 'Academic Sapphire' },
  { name: 'Forest Green', hex: '#16A34A', description: 'Scholastic Emerald' },
  { name: 'Deep Purple', hex: '#7C3AED', description: 'Modern Violet' },
  { name: 'Warm Amber', hex: '#EA580C', description: 'Terracotta Flame' },
  { name: 'Ocean Teal', hex: '#0D9488', description: 'Pacific Cyan' },
];
