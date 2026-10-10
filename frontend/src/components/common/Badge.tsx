import React from 'react';
import './common.css';

export interface BadgeProps {
  variant?: 'jade' | 'crimson' | 'terracotta' | 'gold' | 'neutral';
  size?: 'sm' | 'md';
  icon?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}

export const Badge: React.FC<BadgeProps> = ({
  variant = 'neutral',
  size = 'md',
  icon,
  children,
  className = '',
}) => {
  return (
    <span className={`app-badge app-badge--${variant} app-badge--${size} ${className}`}>
      {icon && <span className="app-badge__icon">{icon}</span>}
      <span className="app-badge__content">{children}</span>
    </span>
  );
};
