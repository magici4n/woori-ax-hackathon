import React, { useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

export function ReasonPopover({ name, reason }) {
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState(null);
  const trigger = useRef(null);
  const bubble = useRef(null);
  const id = useId();

  useLayoutEffect(() => {
    if (!open) return;
    const update = () => {
      const anchor = trigger.current.getBoundingClientRect();
      const panel = bubble.current;
      panel.style.width = `${Math.min(360, window.innerWidth - 32)}px`;
      panel.querySelector('p').style.maxHeight = `${Math.max(60, Math.min(240, anchor.top - 100))}px`;
      const bounds = panel.getBoundingClientRect();
      const left = Math.max(16, Math.min(anchor.right - bounds.width, window.innerWidth - bounds.width - 16));
      setPosition({ left, top: Math.max(12, anchor.top - bounds.height - 12),
        '--reason-arrow-left': `${Math.max(18, Math.min(bounds.width - 18, anchor.left + anchor.width / 2 - left))}px` });
    };
    const outside = event => {
      if (!trigger.current.contains(event.target) && !bubble.current.contains(event.target)) setOpen(false);
    };
    const escape = event => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        setOpen(false);
        trigger.current.focus();
      }
    };
    update();
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape, true);
    window.addEventListener('scroll', update, true);
    window.addEventListener('resize', update);
    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', escape, true);
      window.removeEventListener('scroll', update, true);
      window.removeEventListener('resize', update);
    };
  }, [open]);

  return <><button ref={trigger} type="button" className="report-reason-trigger" aria-label={`${name} 판단 근거 보기`} aria-expanded={open} aria-describedby={open ? id : undefined} onClick={() => { setPosition(null); setOpen(value => !value); }}><span aria-hidden="true">{open ? '▾' : '▸'}</span> 근거 보기</button>
    {open && createPortal(<div ref={bubble} id={id} role="tooltip" className="report-reason-bubble" style={{ ...position, visibility: position ? 'visible' : 'hidden' }}><strong>{name} · 판단 근거</strong><p>{reason}</p></div>, document.body)}</>;
}
