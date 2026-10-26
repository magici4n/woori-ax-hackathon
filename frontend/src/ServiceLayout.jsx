import React from 'react';

const links = [
  ['assets', 'My자산'],
  ['scenarios', '절세 시뮬레이션'],
  ['conditions', '내 투자 의사'],
  ['evidence', '근거 자료'],
];

export function ServiceHeader({ active, onNavigate, canCompare = true }) {
  return <>
    <a className="skip-link" href="#main-content">본문 바로가기</a>
    <header className="service-header" id="top">
      <div className="brand-bar">
        <div className="header-inner">
          <a className="securities-brand" href="#top" onClick={e => { e.preventDefault(); onNavigate('assets'); }} aria-label="우리투자증권 절세 시나리오 처음으로">
            <span className="brand-emblem" aria-hidden="true"/>
            <span>우리투자증권</span>
          </a>
          <span className="service-switch">해외주식 절세 비교</span>
          <span className="prototype-label">해커톤 프로토타입</span>
        </div>
      </div>
      <div className="navigation-bar">
        <nav className="header-inner" aria-label="주요 서비스">
          {links.map(([id, label]) => <button key={id} type="button"
            disabled={!canCompare && (id === 'conditions' || id === 'evidence')}
            className={active === id ? 'current' : ''}
            aria-current={active === id ? 'step' : undefined}
            onClick={() => onNavigate(id)}>{label}</button>)}
          <span className="navigation-note">결과를 보고, 내 생각을 더해요</span>
        </nav>
      </div>
    </header>
  </>;
}

export function ServiceHero({ portfolio, onPrepareConditions }) {
  const tax = new Intl.NumberFormat('ko-KR').format(portfolio.estimatedTaxKrw);
  return <section className="service-hero" aria-labelledby="hero-title">
    <div className="hero-copy">
      <span className="hero-category">내 보유 의사를 담은 절세 시나리오</span>
      <h1 id="hero-title">팔고 싶지 않은 종목,<br/><strong>먼저 이야기해 주세요.</strong></h1>
      <p>흩어진 해외주식 손익을 모아,<br/>대화로 정한 내 조건 안에서 절세 시나리오를 비교해요.</p>
      <div className="hero-actions">
        <button className="button primary" onClick={onPrepareConditions}>AI와 내 조건 이야기하기 <span aria-hidden="true">→</span></button>
        <span className="hero-secondary">보유 의사 확인부터 결과 비교까지</span>
      </div>
    </div>
    <div className="hero-visual" aria-hidden="true">
      <div className="visual-orbit orbit-one"/><div className="visual-orbit orbit-two"/>
      <div className="visual-back-card"><span>MY INVESTMENT</span><i/><i/><i/></div>
      <div className="visual-receipt">
        <div className="receipt-heading"><span className="receipt-symbol">W</span><span>나의 절세 시나리오</span></div>
        <span className="receipt-caption">현재 예상 세금</span>
        <strong>{tax}<small>원</small></strong>
        <div className="receipt-rule"/>
        <div className="receipt-check"><span>✓</span><div>보유 의사 확인<small>내 조건에 맞춰 비교해요</small></div></div>
        <div className="receipt-check"><span>✓</span><div>증권사 손익 합산<small>흩어진 손익을 한눈에</small></div></div>
      </div>
      <span className="visual-check">✓</span>
      <span className="visual-coin coin-one">₩</span><span className="visual-coin coin-two">₩</span>
    </div>
  </section>;
}

export function JourneyNav({ active, onNavigate, canCompare }) {
  const steps = [['assets', '01', '내 자산 확인'], ['scenarios', '02', '시뮬레이션 · 근거 확인'], ['conditions', '03', '대화 후 다시 시뮬레이션']];
  return <nav className="flow-steps" aria-label="이용 순서">{steps.map(([id, n, title]) =>
    <button key={id} disabled={id === 'conditions' && !canCompare} onClick={() => onNavigate(id)}
      className={active === id ? 'current' : ''} aria-current={active === id ? 'step' : undefined}>
      <span>{n}</span><strong>{title}</strong><i aria-hidden="true">›</i>
    </button>)}
  </nav>;
}
