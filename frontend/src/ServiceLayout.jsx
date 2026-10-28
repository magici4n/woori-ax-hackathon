import React from 'react';

export function ServiceHeader({ onNavigate }) {
  return <><a className="skip-link" href="#main-content">본문 바로가기</a><header className="service-header reference-header"><div className="reference-header-inner"><button className="reference-brand" onClick={() => onNavigate('assets')} aria-label="처음으로"><span className="brand-emblem" aria-hidden="true"/>우리투자증권</button><nav aria-label="주요 메뉴"><span>투자</span><span>자산관리</span><span>해외서비스</span><span>고객센터</span></nav><span className="reference-header-end">절세 WON해?</span></div></header></>;
}

export function JourneyNav({ active, onNavigate, canCompare }) {
  const steps = [['analysis', '내 계좌 분석'], ['events', '이벤트 분석'], ['conditions', '맞춤 조건 설정'], ['scenarios', '절세 시나리오'], ['report', '최종 리포트']];
  const current = active === 'assets' ? -1 : steps.findIndex(([id]) => id === active);
  return <nav className="reference-journey" aria-label="절세 시나리오 진행 단계">{steps.map(([id, label], index) => <button key={id} className={index === current ? 'current' : index < current ? 'done' : ''} disabled={(!canCompare && index > 0) || (index > current && !['events', 'conditions'].includes(id))} onClick={() => onNavigate(id)} aria-current={index === current ? 'step' : undefined}><span>{index + 1}</span><strong>{label}</strong></button>)}</nav>;
}
