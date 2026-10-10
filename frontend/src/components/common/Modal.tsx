import React, { useEffect, useRef } from 'react';
import { X } from 'lucide-react';
import './common.css';

export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: React.ReactNode;
  description?: React.ReactNode;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  children: React.ReactNode;
  footer?: React.ReactNode;
}

export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  description,
  size = 'md',
  children,
  footer,
}) => {
  const modalRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    if (isOpen) {
      document.addEventListener('keydown', handleKeyDown);
      document.body.style.overflow = 'hidden';
    }

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = '';
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="app-modal-backdrop" onClick={onClose} aria-hidden="true">
      <div
        className={`app-modal-container app-modal-container--${size}`}
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        ref={modalRef}
      >
        <header className="app-modal-header">
          <div className="app-modal-titles">
            <h2 className="app-modal-title">{title}</h2>
            {description && <p className="app-modal-description">{description}</p>}
          </div>
          <button
            className="app-modal-close-btn"
            onClick={onClose}
            aria-label="Close dialog"
          >
            <X size={18} />
          </button>
        </header>

        <div className="app-modal-body">{children}</div>

        {footer && <footer className="app-modal-footer">{footer}</footer>}
      </div>
    </div>
  );
};
