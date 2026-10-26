import React, { useState } from 'react';
import { Icon, StockMark, won, signedWon, names, SourceList } from './ui.jsx';

export function AssetOverview({ portfolio, onSimulate, busy }) {
  return <div className="overview-stage asset-first">
    <div className="flow-page-heading"><div><span className="eyebrow">MY PORTFOLIO</span><h1>내 투자 자산</h1><p>계좌별로 흩어진 해외주식, 한눈에 살펴보세요. · {portfolio.rule.asOf} 기준</p></div><button className="button primary" disabled={busy} onClick={onSimulate}>절세 시뮬레이션 시작 <Icon name="arrow" size={18}/></button></div>
    <div className="summary-grid flow-summary">
      <div className="summary-card"><span>해외주식 총 평가액</span><strong>{won(portfolio.marketValueKrw)}</strong><small>평가손익 <b className={portfolio.unrealizedKrw < 0 ? 'negative' : 'positive'}>{signedWon(portfolio.unrealizedKrw)}</b></small></div>
      <div className="summary-card"><span>올해 합산 실현손익</span><strong>{signedWon(portfolio.realizedKrw)}</strong><small>{portfolio.accounts.length}개 시연 계좌 손익 합산</small></div>
      <div className="summary-card blue"><span>현재 예상 양도소득세</span><strong>{won(portfolio.estimatedTaxKrw)}</strong><small>기본공제 {won(portfolio.rule.deductionKrw)} 반영</small></div>
    </div>
    <section className="panel holdings-overview"><div className="section-heading"><div><span className="eyebrow">INVESTMENTS</span><h2>내가 보유한 종목</h2></div><span className="tag">4개 종목 · 모든 계좌 합산</span></div>
      <div className="table-scroll"><table className="assets-table"><thead><tr><th>종목</th><th>보유 수량</th><th>취득가액</th><th>평가액</th><th>원화 평가손익</th></tr></thead><tbody>{Object.entries(names).map(([symbol, name]) => {
        const rows = portfolio.positions.filter(p => p.lot.symbol === symbol);
        const sum = fn => rows.reduce((total, p) => total + Number(fn(p)), 0);
        const pnl = sum(p => p.unrealizedKrw);
        return <tr key={symbol}><td><div className="stock-cell"><StockMark symbol={symbol}/><div><strong>{name}</strong><small>{symbol}</small></div></div></td><td><strong>{sum(p => p.lot.quantity)}주</strong></td><td>{won(sum(p => p.lot.acquisitionKrw))}</td><td><strong>{won(sum(p => p.marketValueKrw))}</strong></td><td className={pnl < 0 ? 'negative' : 'positive'}>{signedWon(pnl)}</td></tr>;
      })}</tbody></table></div>
      <details className="calculation-detail"><summary>계좌별 보유 내역 보기</summary><div className="table-scroll"><table><thead><tr><th>계좌</th><th>종목</th><th>수량</th><th>평가액</th><th>가상 매도 시 원화 세무 손익</th></tr></thead><tbody>{portfolio.positions.map(p => <tr key={p.lot.id}><td>{p.lot.broker} · {p.lot.account}</td><td>{p.lot.name}</td><td>{p.lot.quantity}주</td><td>{won(p.marketValueKrw)}</td><td>{signedWon(p.hypotheticalTaxGainKrw)}</td></tr>)}</tbody></table></div><p>원화 세무 손익에는 취득 당시 원가와 추가 매도 비용을 반영합니다.</p></details>
    </section>
    <section className="simulation-launch"><span className="large-chat-orb"><Icon name="chart" size={25}/></span><div><span className="eyebrow">세금과 보유 기회, 함께 비교해요</span><h2>내 자산으로 얼마나 절세할 수 있을까요?</h2><p>세 가지 시나리오와 선택 이유를 먼저 보고, 투자 의사는 대화로 반영하세요.</p></div><button className="button primary" disabled={busy} onClick={onSimulate}>절세 시뮬레이션 시작 <Icon name="arrow" size={18}/></button></section>
  </div>;
}
function ChatInvitation({ onChat }) {
  return <section className="chat-invitation"><span className="large-chat-orb"><Icon name="spark" size={25}/></span><div><h2>팔고 싶지 않은 주식이 있나요?</h2><p>“테슬라는 장기투자라 안 팔래요.” 내 생각을 말하면 조건을 바꿔 다시 비교해 드려요.</p></div><button className="button primary" onClick={onChat}>내 투자 의사 이야기하기 <Icon name="arrow" size={17}/></button></section>;
}
export function PortfolioResults({ comparison, previous, research, explanation, explainBusy, onExplain, onChat, conditionSummary }) {
  const [selectedId, setSelectedId] = useState('tax');
  const selected = comparison.scenarios.find(s => s.id === selectedId) || comparison.scenarios[2];
  const sources = research?.sources || [], notes = research?.notes || [];
  return <>
    <section className="simulation-result-summary"><div><span>현재 예상 세금</span><strong>{won(comparison.baselineTaxKrw)}</strong></div><Icon name="arrow" size={24}/><div><span>조건 내 최대 감소액</span><strong className="saving-amount">{won(comparison.maxTaxReductionKrw)}</strong></div><p>매도 비용과 원화 손익을 반영한 결과<br/><small>전체 보유 종목을 함께 비교했어요.</small></p></section>
    {previous && <div className="revision-banner"><Icon name="check" size={18}/><div><strong>대화에서 정한 조건으로 다시 계산했어요.</strong><p>최대 감소액 {won(previous.maxTaxReductionKrw)} → {won(comparison.maxTaxReductionKrw)} · 이전보다 {signedWon(Number(comparison.maxTaxReductionKrw) - Number(previous.maxTaxReductionKrw))}</p>{conditionSummary}</div></div>}
    <div className="section-heading result-cards-heading"><div><span className="eyebrow">THREE SCENARIOS</span><h2>절세와 보유, 무엇을 우선할까요?</h2></div><span className="muted small">카드를 선택하면 종목별 이유를 볼 수 있어요.</span></div>
    <div className="scenario-cards portfolio-scenarios">{comparison.scenarios.map((s, i) => {
      const sales = s.holdings.filter(h => h.sellQuantity > 0), ai = explanation?.scenarios?.find(item => item.id === s.id);
      return <article key={s.id} className={'scenario-card ' + (s.id === selected.id ? 'highlight selected-plan' : '')}>
        <div className="scenario-label"><span>시나리오 {i + 1}</span><span className="tag">{s.retainedPercent}% 보유</span></div><h3>{s.title}</h3>
        <p className="plan-sales">{sales.length ? sales.map(h => `${h.name} ${h.sellQuantity}주 매도`).join(' · ') : '모든 주식 그대로 보유'}</p>
        <div className="plan-saving"><span>예상 세금 감소</span><strong>{won(s.taxReductionKrw)}</strong></div>
        <dl className="metrics"><div><dt>올해 예상 세금</dt><dd>{won(s.estimatedTaxKrw)}</dd></div><div><dt>추가 매도 비용</dt><dd>{won(s.saleFeeKrw)}</dd></div><div><dt>확정하는 손익</dt><dd className={s.realizedGainKrw < 0 ? 'negative' : ''}>{signedWon(s.realizedGainKrw)}</dd></div></dl>
        <div className="selection-reason"><strong>이 시나리오를 제시한 이유</strong><p>{s.reason}</p></div>
        {ai && <details className="scenario-ai-note"><summary>AI가 읽은 기업 근거</summary><p>{ai.text}</p><div>{ai.sourceIds.map(id => { const source = sources.find(item => item.id === id); return source && <a key={id} href={source.url} target="_blank" rel="noreferrer">{source.symbols.length ? source.symbols.join(' · ') + ' 실적' : '세무 근거'} ↗</a>; })}</div></details>}
        {s.duplicateOf && <p className="duplicate">현재 조건에서는 시나리오 {comparison.scenarios.findIndex(item => item.id === s.duplicateOf) + 1}과 같은 결과예요.</p>}
        {!s.targetMet && <p className="error-text">희망 감소액 미충족</p>}
        <button className={'button wide ' + (s.id === selected.id ? 'primary' : 'light')} aria-pressed={s.id === selected.id} onClick={() => setSelectedId(s.id)}>{s.id === selected.id ? '선택한 시나리오' : '종목별 이유 보기'} <Icon name="arrow" size={16}/></button>
      </article>;
    })}</div>
    <div className="ai-explanation-status" aria-live="polite">{explainBusy ? <span>AI가 기업 자료를 읽고 각 시나리오의 해설을 더하고 있어요…</span> : explanation?.mode === 'unavailable' ? <><span>{explanation.message}</span><button onClick={onExplain}>해설 다시 요청</button></> : <span>수량과 세액은 계산 엔진, AI 해설은 아래 출처를 사용합니다.</span>}</div>
    <ChatInvitation onChat={onChat}/>
    <section className="panel selected-plan-detail"><div className="section-heading"><div><span className="eyebrow">선택한 시나리오 상세</span><h2>{selected.title} — 어떤 종목이 바뀌나요?</h2></div></div><div className="table-scroll"><table><thead><tr><th>종목</th><th>가상 매도</th><th>계속 보유</th><th>계산에 포함한 이유</th></tr></thead><tbody>{selected.holdings.map(h => <tr key={h.symbol}><td><div className="stock-cell"><StockMark symbol={h.symbol}/><strong>{h.name}</strong></div></td><td>{h.sellQuantity}주</td><td>{h.remainingQuantity}주</td><td>{h.reason}</td></tr>)}</tbody></table></div><p className="plan-tradeoff"><Icon name="info" size={18}/>{selected.tradeoff}</p></section>
    <section className="research-section" id="evidence"><div className="section-heading"><div><span className="eyebrow">BUSINESS & EVIDENCE</span><h2>세금만 보고 결정하기 전에</h2><p className="muted">보유 기업의 실제 발표에서 성장 요인과 위험을 함께 확인하세요.</p></div><span className="tag">원문 확인한 기업 자료</span></div>
      <p className="research-disclosure">2026-10-27에 원문을 확인해 저장한 자료입니다. 실시간 뉴스 피드가 아니며, 실적 발표일 이후 변화나 현재 주가의 적정성을 모두 반영하지 않습니다.</p>
      <div className="research-grid">{notes.map(note => { const source = sources.find(s => s.id === note.sourceId), holding = selected.holdings.find(h => h.symbol === note.symbol); return <article className="panel research-card" key={note.symbol}>
        <div className="research-card-top"><StockMark symbol={note.symbol}/><div><h3>{names[note.symbol]}</h3><span>{holding?.sellQuantity ? `${holding.sellQuantity}주 매도 · ${holding.remainingQuantity}주 보유` : '전량 보유'} 가정</span></div></div>
        <div className="research-factor positive-factor"><strong>성장 참고 요인</strong><p>{note.positive}</p></div><div className="research-factor risk-factor"><strong>함께 볼 위험</strong><p>{note.risk}</p></div><p className="research-relevance">{note.relevance}</p>
        {source && <a className="research-source" href={source.url} target="_blank" rel="noreferrer"><span>{source.title} ↗</span><small>{source.kind === 'filing' ? '공시' : '기업 공식 발표'} · {source.publishedAt} · 수집 {source.collectedAt}</small></a>}
      </article>; })}</div>
      {!notes.length && <p className="notice">기업 자료를 불러오지 못했어요. 확인되지 않은 전망은 표시하지 않습니다.</p>}
      <details className="panel calculation-detail"><summary>세금 계산의 근거와 전체 출처 보기</summary><SourceList sources={sources}/></details>
    </section>
    <section className="panel portfolio-futures"><div className="section-heading"><div><span className="eyebrow">가격이 변한다면</span><h2>보유 기회와 하락 위험도 함께 비교해요</h2></div><span className="tag">예측값 아님</span></div><p className="muted">남은 모든 주식 가격이 함께 -10%, 0%, +10% 변한다고 가정한 순자산입니다. 환율은 고정합니다.</p><div className="table-scroll"><table><thead><tr><th>시나리오</th><th>-10%</th><th>변동 없음</th><th>+10%</th></tr></thead><tbody>{comparison.scenarios.map(s => <tr key={s.id}><td>{s.title}</td>{s.futures.map(f => <td key={f.label}>{won(f.netAssetsKrw)}</td>)}</tr>)}</tbody></table></div><small>매도 후 현금 + 남은 주식 평가액 − 올해 예상 세금. 수수료는 현금에 한 번 반영하며, 재투자 수익과 미래 잠재 세금은 포함하지 않습니다.</small></section>
    <details className="panel calculation-detail"><summary>시나리오 구성 기준과 계산 범위</summary>{comparison.notices.map(n => <p key={n}>{n}</p>)}</details>
    <ChatInvitation onChat={onChat}/>
  </>;
}
