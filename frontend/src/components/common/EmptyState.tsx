import React from 'react';
import { AlertOctagon, HelpCircle, RefreshCw } from 'lucide-react';
import { Button } from './Button';
import './common.css';

export interface EmptyStateProps {
  icon?: React.ReactNode;
  title: string;
  description: string;
  actionText?: string;
  onAction?: () => void;
  className?: string;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  icon,
  title,
  description,
  actionText,
  onAction,
  className = '',
}) => {
  return (
    <div className={`app-empty-state ${className}`}>
      <div className="app-empty-state__icon">
        {icon || <HelpCircle size={36} />}
      </div>
      <h4 className="app-empty-state__title">{title}</h4>
      <p className="app-empty-state__description">{description}</p>
      {actionText && onAction && (
        <div className="app-empty-state__action">
          <Button variant="primary" size="sm" onClick={onAction}>
            {actionText}
          </Button>
        </div>
      )}
    </div>
  );
};

export interface ErrorBannerProps {
  title?: string;
  message: string;
  details?: string;
  onRetry?: () => void;
  className?: string;
}

export const ErrorBanner: React.FC<ErrorBannerProps> = ({
  title = 'Service Communication Error',
  message,
  details,
  onRetry,
  className = '',
}) => {
  const [showDetails, setShowDetails] = React.useState(false);

  return (
    <div className={`app-error-banner ${className}`} role="alert">
      <div className="app-error-banner__header">
        <AlertOctagon className="app-error-banner__icon" size={20} />
        <div className="app-error-banner__content">
          <strong className="app-error-banner__title">{title}</strong>
          <p className="app-error-banner__message">{message}</p>
          {details && (
            <div className="app-error-banner__details-toggle">
              <button
                type="button"
                className="app-error-banner__link-btn"
                onClick={() => setShowDetails(!showDetails)}
              >
                {showDetails ? 'Hide technical diagnostics ▲' : 'Show technical diagnostics ▼'}
              </button>
              {showDetails && (
                <pre className="app-error-banner__pre">
                  <code>{details}</code>
                </pre>
              )}
            </div>
          )}
        </div>
        {onRetry && (
          <Button
            variant="outline"
            size="sm"
            leftIcon={<RefreshCw size={14} />}
            onClick={onRetry}
            className="app-error-banner__retry-btn"
          >
            Retry
          </Button>
        )}
      </div>
    </div>
  );
};
