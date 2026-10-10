import React from 'react';
import './common.css';

export interface LoadingSpinnerProps {
  size?: 'sm' | 'md' | 'lg';
  color?: 'gold' | 'terracotta' | 'jade' | 'ivory';
  label?: string;
  className?: string;
}

export const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  size = 'md',
  color = 'gold',
  label,
  className = '',
}) => {
  return (
    <div className={`app-spinner-container ${className}`} role="status">
      <div className={`app-spinner app-spinner--${size} app-spinner--${color}`} />
      {label && <span className="app-spinner-label">{label}</span>}
      <span className="sr-only">Loading...</span>
    </div>
  );
};

export interface LoadingSkeletonProps {
  width?: string | number;
  height?: string | number;
  borderRadius?: string | number;
  className?: string;
}

export const LoadingSkeleton: React.FC<LoadingSkeletonProps> = ({
  width = '100%',
  height = '1rem',
  borderRadius = 'var(--radius-sm)',
  className = '',
}) => {
  return (
    <div
      className={`app-skeleton ${className}`}
      style={{
        width: typeof width === 'number' ? `${width}px` : width,
        height: typeof height === 'number' ? `${height}px` : height,
        borderRadius: typeof borderRadius === 'number' ? `${borderRadius}px` : borderRadius,
      }}
      aria-hidden="true"
    />
  );
};
