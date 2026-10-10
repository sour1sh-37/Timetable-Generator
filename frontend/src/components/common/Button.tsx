import React from 'react';
import './common.css';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger' | 'gold';
  size?: 'sm' | 'md' | 'lg';
  isLoading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

export const Button: React.FC<ButtonProps> = ({
  children,
  variant = 'primary',
  size = 'md',
  isLoading = false,
  leftIcon,
  rightIcon,
  disabled,
  className = '',
  ...props
}) => {
  return (
    <button
      className={`app-btn app-btn--${variant} app-btn--${size} ${isLoading ? 'is-loading' : ''} ${className}`}
      disabled={disabled || isLoading}
      {...props}
    >
      {isLoading ? (
        <span className="app-btn__spinner" aria-hidden="true" />
      ) : (
        leftIcon && <span className="app-btn__icon-left">{leftIcon}</span>
      )}
      <span className="app-btn__text">{children}</span>
      {!isLoading && rightIcon && <span className="app-btn__icon-right">{rightIcon}</span>}
    </button>
  );
};
