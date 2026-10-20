// Test script for Color Utils verification
function clamp(val, min, max) {
  return Math.min(Math.max(val, min), max);
}

function isValidHex(hex) {
  if (!hex) return false;
  const clean = hex.startsWith('#') ? hex.slice(1) : hex;
  return /^[0-9A-Fa-f]{3}$|^[0-9A-Fa-f]{6}$/.test(clean);
}

function normalizeHex(hex, fallback = '#8B1E29') {
  if (!isValidHex(hex)) return fallback;
  let clean = hex.startsWith('#') ? hex.slice(1) : hex;
  if (clean.length === 3) {
    clean = clean.split('').map((c) => c + c).join('');
  }
  return `#${clean.toUpperCase()}`;
}

function hexToRgb(hex) {
  const norm = normalizeHex(hex);
  const clean = norm.slice(1);
  const r = parseInt(clean.substring(0, 2), 16);
  const g = parseInt(clean.substring(2, 4), 16);
  const b = parseInt(clean.substring(4, 6), 16);
  return { r, g, b };
}

function rgbToHex(r, g, b) {
  const toHex = (n) => {
    const clamped = clamp(Math.round(n), 0, 255);
    return clamped.toString(16).padStart(2, '0').toUpperCase();
  };
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

function rgbToHsv(r, g, b) {
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

function hsvToRgb(h, s, v) {
  const hClamped = (clamp(h, 0, 360) % 360 + 360) % 360;
  const sClamped = clamp(s, 0, 1);
  const vClamped = clamp(v, 0, 1);

  const c = vClamped * sClamped;
  const x = c * (1 - Math.abs(((hClamped / 60) % 2) - 1));
  const m = vClamped - c;

  let rPrime = 0, gPrime = 0, bPrime = 0;

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

function hexToHsv(hex) {
  const rgb = hexToRgb(hex);
  return rgbToHsv(rgb.r, rgb.g, rgb.b);
}

function hsvToHex(h, s, v) {
  const rgb = hsvToRgb(h, s, v);
  return rgbToHex(rgb.r, rgb.g, rgb.b);
}

// Test cases
const testColors = [
  '#FF0000', '#00FF00', '#0000FF', '#FFFFFF', '#000000',
  '#8B1E29', '#2563EB', '#16A34A', '#7C3AED', '#EA580C', '#0D9488'
];

let failed = 0;
for (const color of testColors) {
  const hsv = hexToHsv(color);
  const hexBack = hsvToHex(hsv.h, hsv.s, hsv.v);
  if (color !== hexBack) {
    console.error(`Mismatch for ${color}: got ${hexBack} (hsv: ${JSON.stringify(hsv)})`);
    failed++;
  } else {
    console.log(`✓ ${color} roundtrip OK: HSV(${hsv.h}, ${(hsv.s*100).toFixed(0)}%, ${(hsv.v*100).toFixed(0)}%)`);
  }
}

// Test validation
console.assert(isValidHex('#fff') === true, '3 digit hex failed');
console.assert(isValidHex('8B1E29') === true, 'no hash failed');
console.assert(isValidHex('#xyz123') === false, 'invalid chars passed');
console.assert(isValidHex('#12') === false, 'too short passed');

if (failed === 0) {
  console.log('\nALL COLOR UTILS TESTS PASSED (100% Accuracy)');
} else {
  process.exit(1);
}
