import React from 'react';
import './common.css';

export interface CardProps {
  title?: React.ReactNode;
  subtitle?: React.ReactNode;
  headerRight?: React.ReactNode;
  footer?: React.ReactNode;
  accentBorder?: 'default' | 'gold' | 'terracotta' | 'jade' | 'crimson';
  elevated?: boolean;
  className?: string;
  children: React.ReactNode;
}

export const Card: React.FC<CardProps> = ({
  title,
  subtitle,
  headerRight,
  footer,
  accentBorder = 'default',
  elevated = false,
  className = '',
  children,
}) => {
  return (
    <section
      className={`app-card app-card--border-${accentBorder} ${elevated ? 'is-elevated' : ''} ${className}`}
    >
      {(title || subtitle || headerRight) && (
        <header className="app-card__header">
          <div className="app-card__header-titles">
            {title && <h3 className="app-card__title">{title}</h3>}
            {subtitle && <p className="app-card__subtitle">{subtitle}</p>}
          </div>
          {headerRight && <div className="app-card__header-right">{headerRight}</div>}
        </header>
      )}
      <div className="app-card__body">{children}</div>
      {footer && <footer className="app-card__footer">{footer}</footer>}
    </section>
  );
};
