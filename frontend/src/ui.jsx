import React, { useEffect, useRef, useState } from 'react';

export const won = value => new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 }).format(value ?? 0) + '원';
export const number = value => new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 }).format(value ?? 0);
export const signedWon = value => (Number(value) > 0 ? '+' : '') + won(value);
export const names = { TSLA: '테슬라', AAPL: '애플', NVDA: '엔비디아', AMZN: '아마존' };
export const emptyConditions = { symbols: {}, targetReductionKrw: null, cashNeedKrw: null, holdingMonths: null };
export async function api(path, body, signal, method) {
  const response = await fetch('/api' + path, { method: method || (body === undefined ? 'GET' : 'POST'), headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body), signal });
  const data = await response.json();
  if (!response.ok) throw new Error(data.message || '요청을 처리하지 못했습니다.');
  return data;
}
export function Icon({ name, size = 20, ...props }) {
  const paths = {
    grid: <><rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/></>,
    chart: <><path d="M4 4v16h16M8 14l4-5 4 3 5-8"/></>,
    spark: <><path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Z"/></>,
    arrow: <path d="M5 12h14m-5-5 5 5-5 5"/>,
    check: <path d="m5 12 4 4L19 6"/>,
    shield: <><path d="M12 3 4 6v6c0 5 8 9 8 9s8-4 8-9V6l-8-3Z"/><path d="m8 12 3 3 5-5"/></>,
    info: <><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7v1"/></>,
    book: <><path d="M12 5v16M3 4c4-1 6 0 9 1 3-1 5-2 9-1v15c-4-1-6 0-9 1-3-1-5-2-9-1V4Z"/></>,
    send: <><path d="m3 3 19 9-19 9 4-9-4-9Zm4 9h15"/></>,
    close: <path d="m6 6 12 12M18 6 6 18"/>,
    wallet: <><rect x="3" y="5" width="18" height="15" rx="3"/><path d="M3 8V5l14-3v3M16 11h5v5h-5z"/></>,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...props}>{paths[name] || paths.info}</svg>;
}
export function StockMark({ symbol }) { return <span className={'stock-mark ' + symbol}>{symbol === 'TSLA' ? 'T' : symbol === 'AAPL' ? 'a' : symbol === 'NVDA' ? 'N' : 'a↗'}</span>; }
export function ConditionText({ symbol, condition }) {
  return <>{names[symbol]} · {condition.excluded ? '매도 제외' : [condition.maxShares != null ? `최대 ${condition.maxShares}주` : '', condition.maxPercent != null ? `최대 ${condition.maxPercent}%` : ''].filter(Boolean).join(' / ') || '제한 없음'}</>;
}
export function ManualForm({ conditions, positions, onApply, disabled = false }) {
  const [draft, setDraft] = useState(conditions);
  const [error, setError] = useState('');
  useEffect(() => { setDraft(structuredClone(conditions)); setError(''); }, [conditions]);
  const update = (symbol, key, value) => setDraft(prev => ({ ...prev, symbols: { ...prev.symbols, [symbol]: { ...prev.symbols[symbol], [key]: value } } }));
  const quantities = Object.keys(names).map(symbol => ({ symbol, qty: positions.filter(p => p.lot.symbol === symbol).reduce((s, p) => s + p.lot.quantity, 0) }));
  function submit(e) {
    e.preventDefault();
    if (disabled) return;
    for (const { symbol, qty } of quantities) {
      const c = draft.symbols[symbol];
      if (c?.maxShares != null && (!Number.isInteger(c.maxShares) || c.maxShares < 0 || c.maxShares > qty)) { setError(`${names[symbol]} 수량은 0~${qty}주 정수로 입력하세요.`); return; }
      if (c?.maxPercent != null && (c.maxPercent < 0 || c.maxPercent > 100)) { setError('비율은 0~100%로 입력하세요.'); return; }
    }
    setError(''); onApply(draft);
  }
  return <form className="manual-form" onSubmit={submit}>
    <p className="muted small">같은 종목의 모든 시연 계좌에 적용돼요. 수량과 비율을 모두 정하면 더 작은 범위를 적용합니다.</p>
    {quantities.map(({ symbol, qty }) => <div className="manual-stock" key={symbol}>
      <div className="manual-stock-top"><strong>{names[symbol]} <span className="muted">{qty}주</span></strong><label className="check-label"><input type="checkbox" checked={draft.symbols[symbol]?.excluded || false} onChange={e => update(symbol, 'excluded', e.target.checked)}/>매도 제외</label></div>
      <div className="two-inputs"><label>최대 수량<input type="number" min="0" max={qty} step="1" disabled={draft.symbols[symbol]?.excluded} value={draft.symbols[symbol]?.maxShares ?? ''} placeholder="제한 없음" onChange={e => update(symbol, 'maxShares', e.target.value === '' ? null : Number(e.target.value))}/></label><label>최대 비율 (%)<input type="number" min="0" max="100" step="0.1" disabled={draft.symbols[symbol]?.excluded} value={draft.symbols[symbol]?.maxPercent ?? ''} placeholder="제한 없음" onChange={e => update(symbol, 'maxPercent', e.target.value === '' ? null : Number(e.target.value))}/></label></div>
    </div>)}
    <label className="target-label">희망 세금 감소액 (원)<input type="number" min="0" max="1000000000" step="1" value={draft.targetReductionKrw ?? ''} placeholder="예: 300000" onChange={e => setDraft({ ...draft, targetReductionKrw: e.target.value === '' ? null : Number(e.target.value) })}/></label>
      <label className="target-label">필요 현금 (원)<input type="number" min="0" max="10000000000" step="1" value={draft.cashNeedKrw ?? ''} placeholder="예: 5000000" onChange={e => setDraft({ ...draft, cashNeedKrw: e.target.value === '' ? null : Number(e.target.value) })}/></label>
      <label className="target-label">보유 계획 (개월)<input type="number" min="0" max="120" step="1" value={draft.holdingMonths ?? ''} placeholder="예: 12" onChange={e => setDraft({ ...draft, holdingMonths: e.target.value === '' ? null : Number(e.target.value) })}/></label>
    {error && <p role="alert" className="error-text">{error}</p>}
    <button className="button primary wide" type="submit" disabled={disabled}>조건 적용하고 다시 계산</button>
  </form>;
}
export function FutureChart({ comparison, activePrice, setActivePrice }) {
  const scenarios = comparison.scenarios.filter(s => s.allowed && !s.duplicateOf);
  const colors = ['#8b98ab', '#1768e8', '#91b749', '#9b64d9'];
  const values = scenarios.flatMap(s => s.futures.map(f => Number(f.netAssetsKrw)));
  const min = Math.min(...values) * 0.985, max = Math.max(...values) * 1.015;
  const x = i => 48 + i * 218;
  const y = value => 150 - (Number(value) - min) / (max - min || 1) * 125;
  return <section className="panel future-section" id="future">
    <div className="section-heading"><div><span className="eyebrow">가격 가정별 비교</span><h2>주가가 달라지면 어떨까요?</h2><p className="muted">3개월 뒤 분석 종목의 가격만 바꿔, 전체 시연 자산을 비교해요.</p></div><span className="tag">예측값 아님</span></div>
    <div className="future-layout"><div className="future-plot"><svg viewBox="0 0 540 185" role="img" aria-label="주가 -10%, 0%, +10%일 때 시나리오별 순자산 비교 그래프">
      {[40, 95, 150].map(yv => <line key={yv} x1="30" x2="510" y1={yv} y2={yv} stroke="#e8edf5" strokeDasharray="4 5"/>)}
      {scenarios.map((s, j) => <g key={s.id}><polyline fill="none" stroke={colors[j]} strokeWidth="2.5" points={s.futures.map((f, i) => `${x(i)},${y(f.netAssetsKrw)}`).join(' ')}/>{s.futures.map((f, i) => <circle key={f.label} cx={x(i)} cy={y(f.netAssetsKrw)} r={i === activePrice ? 5 : 3} fill={colors[j]}><title>{s.title}: {f.label} {won(f.netAssetsKrw)}</title></circle>)}</g>)}
      {['-10%', '현재와 같음', '+10%'].map((t, i) => <text key={t} x={x(i)} y="178" textAnchor="middle" fill="#6c788b" fontSize="12">{t}</text>)}
    </svg></div><div className="future-numbers"><div className="segmented" aria-label="가격 가정 선택">{['-10%', '0%', '+10%'].map((t, i) => <button key={t} className={activePrice === i ? 'active' : ''} onClick={() => setActivePrice(i)}>{t}</button>)}</div>{scenarios.map((s, j) => <div className="future-row" key={s.id}><span><i style={{ background: colors[j] }}/>{s.title.replace('세금 감소를 위한 ', '')}</span><strong>{won(s.futures[activePrice].netAssetsKrw)}</strong></div>)}</div></div>
    <details className="calculation-detail"><summary>비교 기준과 전체 수치 보기</summary><p>매도 후 달러 현금 환산액 + 전체 잔여 주식 평가액 − 올해 전체 예상 세금. 매도 비용은 현금에 한 번만 반영합니다. 분석 종목에만 가격 가정을 적용하며 다른 종목 가격과 환율은 고정합니다. 잔여 주식의 미래 잠재 세금은 포함하지 않습니다.</p><div className="table-scroll"><table><thead><tr><th>시나리오</th><th>-10%</th><th>0%</th><th>+10%</th></tr></thead><tbody>{scenarios.map(s => <tr key={s.id}><td>{s.title}</td>{s.futures.map(f => <td key={f.label}>{won(f.netAssetsKrw)}</td>)}</tr>)}</tbody></table></div></details>
  </section>;
}
export function SourceList({ sources }) {
  return <div className="source-list">{sources.map(source => <details key={source.id} className="source-item"><summary><span className="source-type">{source.kind === 'tax' ? '세무' : source.kind === 'filing' ? '공시' : '기업 발표'}</span><span>{source.title}<small>발행 {source.publishedAt || '일자 확인 불가'} · 수집 {source.collectedAt}</small></span><span className="source-plus">+</span></summary><p>{source.summary}</p><a href={source.url} target="_blank" rel="noopener noreferrer">원문 확인 ↗</a><code>{source.id}</code></details>)}</div>;
}
