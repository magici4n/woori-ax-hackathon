import React, { useId } from 'react';
import { won } from './ui.jsx';

// Decorative artwork stays separate from account values and analysis state.
export function HeroArtwork({ label = '양도소득세', variant = 'tax' }) {
  const id = useId().replace(/:/g, '');
  return <svg className={'presentation-art art-' + variant} viewBox="0 0 480 340" fill="none" aria-hidden="true">
    <defs>
      <linearGradient id={id + '-glass'} x1="100" y1="50" x2="360" y2="320" gradientUnits="userSpaceOnUse"><stop stopColor="white" stopOpacity=".98"/><stop offset="1" stopColor="#cfe5ff" stopOpacity=".85"/></linearGradient>
      <linearGradient id={id + '-blue'} x1="0" y1="0" x2="1" y2="1"><stop stopColor="#19beff"/><stop offset=".55" stopColor="#087aff"/><stop offset="1" stopColor="#0050ed"/></linearGradient>
      <linearGradient id={id + '-ribbon'} x1="80" y1="230" x2="410" y2="80" gradientUnits="userSpaceOnUse"><stop stopColor="#c5e3ff" stopOpacity="0"/><stop offset="1" stopColor="#76b5ff" stopOpacity=".65"/></linearGradient>
      <filter id={id + '-shadow'} x="-40%" y="-40%" width="180%" height="200%"><feDropShadow dx="0" dy="16" stdDeviation="13" floodColor="#3781cf" floodOpacity=".2"/></filter>
    </defs>
    <ellipse cx="260" cy="165" rx="204" ry="88" stroke={'url(#' + id + '-ribbon)'} strokeWidth="40" transform="rotate(-29 260 165)"/>
    <g filter={'url(#' + id + '-shadow)'} transform="rotate(-8 240 170)">
      <rect x="66" y="46" width="133" height="128" rx="21" fill={'url(#' + id + '-glass)'} stroke="white" strokeWidth="2"/>
      {[31, 49, 72].map((height, i) => <rect key={height} x={98 + i * 23} y={144 - height} width="17" height={height} rx="4" fill={'url(#' + id + '-blue)'} opacity={.55 + i * .2}/>)}
      <rect x="153" y="118" width="260" height="171" rx="23" fill={'url(#' + id + '-glass)'} stroke="white" strokeWidth="2"/>
      <text x="181" y="166" fill="#0061ed" fontSize="24" fontWeight="750">{label}</text>
      <rect x="181" y="187" width="195" height="10" rx="5" fill="#b8d8ff"/>
      <rect x="181" y="210" width="157" height="10" rx="5" fill="#b8d8ff"/>
      <rect x="181" y="233" width="111" height="10" rx="5" fill="#b8d8ff"/>
    </g>
    {variant === 'tax' ? <g filter={'url(#' + id + '-shadow)'}><ellipse cx="405" cy="258" rx="43" ry="48" fill={'url(#' + id + '-blue)'} stroke="#d8edff" strokeWidth="2"/><text x="405" y="277" textAnchor="middle" fill="white" fontSize="53" fontWeight="500">$</text></g>
      : <g filter={'url(#' + id + '-shadow)'}><rect x="358" y="206" width="88" height="86" rx="19" fill={'url(#' + id + '-glass)'} stroke="white" strokeWidth="2"/><text x="402" y="263" textAnchor="middle" fill="#0878ff" fontSize="40" fontWeight="800">AI</text><path d="m430 198 4 10 10 4-10 4-4 10-4-10-10-4 10-4Z" fill="#0878ff"/></g>}
  </svg>;
}

export function TaxComparisonChart({ baseline, estimated }) {
  const ceiling = Math.max(Number(baseline), Number(estimated), 1);
  return <div className="presentation-tax-chart" role="img" aria-label={`현재 예상 세금 ${won(baseline)}, 시나리오 적용 후 ${won(estimated)}`}>
    {[['현재 예상 세금', baseline], ['시나리오 적용 후', estimated]].map(([label, value], index) => <div className="tax-chart-column" key={label}>
      <strong>{won(value)}</strong><div className="tax-chart-track"><span className={index ? 'after' : 'before'} style={{ height: `${Math.max(0, Number(value)) / ceiling * 100}%` }}/></div><span>{label}</span>
    </div>)}
  </div>;
}

export function ReportProgress() {
  return <ol className="report-progress" aria-label="최종 리포트 단계">{['내 계좌 분석', '기업 자료', '질문하기', '절세안', '리포트'].map((label, index) => <li key={label} aria-current={index === 4 ? 'step' : undefined}><span>{index + 1}</span><small>{label}</small></li>)}</ol>;
}
