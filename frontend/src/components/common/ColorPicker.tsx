import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  hsvToHex,
  hexToHsv,
  isValidHex,
  normalizeHex,
  clamp,
  ACCENT_PRESETS,
  type HsvColor,
} from '../../utils/colorUtils';
import { Button } from './Button';
import './ColorPicker.css';

export interface ColorPickerProps {
  initialColor?: string;
  onApply: (hex: string) => void;
  onCancel?: () => void;
  onResetDefault?: () => void;
  onPreviewChange?: (hex: string) => void;
}

export const ColorPicker: React.FC<ColorPickerProps> = ({
  initialColor = '#8B1E29',
  onApply,
  onCancel,
  onResetDefault,
  onPreviewChange,
}) => {
  const normInitial = normalizeHex(initialColor);
  const [hsv, setHsv] = useState<HsvColor>(() => hexToHsv(normInitial));
  const [hexInput, setHexInput] = useState<string>(() => normInitial.replace('#', ''));
  const [originalColor] = useState<string>(normInitial);

  const squareRef = useRef<HTMLDivElement>(null);
  const hueRef = useRef<HTMLDivElement>(null);
  const isDraggingSquareRef = useRef(false);
  const isDraggingHueRef = useRef(false);

  const currentHex = hsvToHex(hsv.h, hsv.s, hsv.v);

  // Notify parent of live preview updates
  useEffect(() => {
    if (onPreviewChange) {
      onPreviewChange(currentHex);
    }
  }, [currentHex, onPreviewChange]);

  // Handle pointer down / move on Saturation & Brightness square
  const updateSquareFromPointer = useCallback((clientX: number, clientY: number) => {
    if (!squareRef.current) return;
    const rect = squareRef.current.getBoundingClientRect();
    const x = clamp(clientX - rect.left, 0, rect.width);
    const y = clamp(clientY - rect.top, 0, rect.height);

    const s = x / rect.width;
    const v = 1 - y / rect.height;

    setHsv((prev) => {
      const next = { ...prev, s, v };
      const hex = hsvToHex(next.h, s, v);
      setHexInput(hex.replace('#', ''));
      return next;
    });
  }, []);

  const handleSquarePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    e.preventDefault();
    isDraggingSquareRef.current = true;
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
    updateSquareFromPointer(e.clientX, e.clientY);
  };

  const handleSquarePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!isDraggingSquareRef.current) return;
    updateSquareFromPointer(e.clientX, e.clientY);
  };

  const handleSquarePointerUp = (e: React.PointerEvent<HTMLDivElement>) => {
    if (isDraggingSquareRef.current) {
      isDraggingSquareRef.current = false;
      try {
        (e.target as HTMLElement).releasePointerCapture(e.pointerId);
      } catch {}
    }
  };

  // Handle pointer down / move on Rainbow Hue slider
  const updateHueFromPointer = useCallback((clientX: number) => {
    if (!hueRef.current) return;
    const rect = hueRef.current.getBoundingClientRect();
    const x = clamp(clientX - rect.left, 0, rect.width);
    const h = (x / rect.width) * 360;

    setHsv((prev) => {
      const next = { ...prev, h };
      const hex = hsvToHex(h, next.s, next.v);
      setHexInput(hex.replace('#', ''));
      return next;
    });
  }, []);

  const handleHuePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    e.preventDefault();
    isDraggingHueRef.current = true;
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
    updateHueFromPointer(e.clientX);
  };

  const handleHuePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!isDraggingHueRef.current) return;
    updateHueFromPointer(e.clientX);
  };

  const handleHuePointerUp = (e: React.PointerEvent<HTMLDivElement>) => {
    if (isDraggingHueRef.current) {
      isDraggingHueRef.current = false;
      try {
        (e.target as HTMLElement).releasePointerCapture(e.pointerId);
      } catch {}
    }
  };

  // Handle HEX input change
  const handleHexInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value.replace(/[^0-9A-Fa-f]/g, '').slice(0, 6);
    setHexInput(raw);

    if (isValidHex(raw)) {
      const full = normalizeHex(raw);
      const newHsv = hexToHsv(full);
      setHsv(newHsv);
    }
  };

  const handleHexInputBlur = () => {
    // Re-synchronize with current valid color on blur
    setHexInput(currentHex.replace('#', ''));
  };

  // Select a preset color
  const handleSelectPreset = (hex: string) => {
    const norm = normalizeHex(hex);
    const newHsv = hexToHsv(norm);
    setHsv(newHsv);
    setHexInput(norm.replace('#', ''));
  };

  return (
    <div className="color-picker-card" role="region" aria-label="Custom Accent Color Picker">
      {/* 1. Saturation & Brightness 2D Square */}
      <div
        ref={squareRef}
        className="color-picker__sb-square"
        style={{ backgroundColor: `hsl(${hsv.h}, 100%, 50%)` }}
        onPointerDown={handleSquarePointerDown}
        onPointerMove={handleSquarePointerMove}
        onPointerUp={handleSquarePointerUp}
        aria-label="Saturation and Brightness selector. Drag to adjust."
      >
        <div className="color-picker__sb-white-overlay" />
        <div className="color-picker__sb-black-overlay" />
        <div
          className="color-picker__sb-cursor"
          style={{
            left: `${clamp(hsv.s * 100, 0, 100)}%`,
            top: `${clamp((1 - hsv.v) * 100, 0, 100)}%`,
            backgroundColor: currentHex,
          }}
        />
      </div>

      {/* 2. Swatch & Rainbow Hue Track */}
      <div className="color-picker__controls-row">
        {/* Color preview swatch (clicking reverts to original) */}
        <div
          className="color-picker__preview-swatch"
          style={{ backgroundColor: currentHex }}
          title="Click to revert to original"
          onClick={() => handleSelectPreset(originalColor)}
          role="button"
          tabIndex={0}
          aria-label={`Selected color preview: ${currentHex}`}
        >
          <div
            className="color-picker__preview-split"
            style={{ backgroundColor: originalColor }}
            title={`Initial: ${originalColor}`}
          />
        </div>

        <div className="color-picker__sliders-cluster">
          {/* Rainbow Hue Slider */}
          <div
            ref={hueRef}
            className="color-picker__hue-track"
            onPointerDown={handleHuePointerDown}
            onPointerMove={handleHuePointerMove}
            onPointerUp={handleHuePointerUp}
            aria-label="Color Hue Spectrum Slider"
          >
            <div
              className="color-picker__hue-thumb"
              style={{
                left: `${clamp((hsv.h / 360) * 100, 0, 100)}%`,
              }}
            />
          </div>
        </div>
      </div>

      {/* 3. HEX Input */}
      <div className="color-picker__hex-row">
        <span className="color-picker__hex-label">HEX Value</span>
        <div className="color-picker__hex-input-wrap">
          <span className="color-picker__hex-prefix">#</span>
          <input
            type="text"
            value={hexInput}
            onChange={handleHexInputChange}
            onBlur={handleHexInputBlur}
            maxLength={6}
            className="color-picker__hex-input"
            spellCheck={false}
            aria-label="Hexadecimal color input"
          />
        </div>
      </div>

      {/* 4. Curated Presets Grid */}
      <div className="color-picker__presets-section">
        <span className="color-picker__presets-label">Preset Accents</span>
        <div className="color-picker__presets-grid">
          {ACCENT_PRESETS.map((p) => {
            const isActive = currentHex.toUpperCase() === p.hex.toUpperCase();
            return (
              <button
                key={p.hex}
                type="button"
                className={`color-picker__preset-btn ${isActive ? 'is-active' : ''}`}
                style={{ backgroundColor: p.hex }}
                onClick={() => handleSelectPreset(p.hex)}
                title={`${p.name} (${p.hex})`}
                aria-label={`Select ${p.name}`}
              />
            );
          })}
        </div>
      </div>

      {/* 5. Footer Actions */}
      <div className="color-picker__actions-row">
        {onResetDefault && (
          <Button
            variant="ghost"
            size="sm"
            onClick={onResetDefault}
            title="Reset to SIES Institutional Burgundy"
          >
            Reset Default
          </Button>
        )}
        <div style={{ display: 'flex', gap: '0.5rem', marginLeft: 'auto' }}>
          {onCancel && (
            <Button variant="secondary" size="sm" onClick={onCancel}>
              Cancel
            </Button>
          )}
          <Button
            variant="primary"
            size="sm"
            onClick={() => onApply(currentHex)}
          >
            Apply Accent
          </Button>
        </div>
      </div>
    </div>
  );
};
