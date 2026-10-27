import React, { useState } from 'react';
import { Icon, StockMark, names, won, signedWon } from './ui.jsx';

const heldSymbols = portfolio => [...new Set(portfolio.positions.map(position => position.lot.symbol))];
const positionFor = (portfolio, symbol) => {
  const rows = portfolio.positions.filter(position => position.lot.symbol === symbol);
  return {
    quantity: rows.reduce((sum, position) => sum + Number(position.lot.quantity), 0),
    value: rows.reduce((sum, position) => sum + Number(position.marketValueKrw), 0),
    gain: rows.reduce((sum, position) => sum + Number(position.unrealizedKrw), 0),
  };
};

export function AssetDashboard({ portfolio, onStart, busy }) {
  return <div className="reference-assets">
    <section className="reference-hero">
      <div className="reference-hero-copy"><span className="reference-kicker">해외주식 양도소득세</span>
        <h1>해외주식<br/><em>양도소득세,</em> 미리 준비하세요.</h1>
        <p>내 투자 현황을 살펴보고, 보유 의사와 기업 자료를 함께 반영한 절세 시나리오를 비교해 보세요.</p></div>
      <div className="reference-hero-art" aria-hidden="true"><span className="hero-chart">▥</span><div className="hero-paper"><strong>양도소득세</strong><i/><i/><i/></div><span className="hero-dollar">₩</span></div>
    </section>
    <div className="reference-main-card asset-total"><div><span>현재 투자자산 한눈에 보기</span><strong>{won(portfolio.marketValueKrw)}</strong><small>모든 시연 계좌의 해외주식 평가액</small></div><div><span>현재 예상 양도소득세</span><strong>{won(portfolio.estimatedTaxKrw)}</strong><small>올해 실현손익과 기본공제 반영</small></div></div>
    <section className="reference-holdings"><div className="reference-section-head"><h2>보유 종목</h2><span>{heldSymbols(portfolio).length}개 종목 · {portfolio.rule.asOf} 기준</span></div>
      <div className="table-scroll"><table><thead><tr><th>종목명</th><th>보유 수량</th><th>평가금액</th><th>평가손익</th></tr></thead><tbody>{heldSymbols(portfolio).map(symbol => { const holding = positionFor(portfolio, symbol); return <tr key={symbol}><td><div className="stock-cell"><StockMark symbol={symbol}/><div><strong>{names[symbol] || symbol}</strong><small>{symbol}</small></div></div></td><td>{holding.quantity}주</td><td>{won(holding.value)}</td><td className={holding.gain < 0 ? 'negative' : 'positive'}>{signedWon(holding.gain)}</td></tr>; })}</tbody></table></div>
    </section>
    <div className="reference-bottom-action"><button className="button primary" disabled={busy} onClick={onStart}>{busy ? '계산하고 있어요…' : '내 양도소득세 줄이기'} <Icon name="arrow" size={19}/></button></div>
  </div>;
}

export function AnalysisStep({ portfolio, comparison, busy, error, onContinue, onRetry }) {
  return <div className="analysis-page"><section className="reference-hero compact"><div className="reference-hero-copy"><span className="reference-kicker">내 계좌 분석</span><h1>고객님의<br/><em>투자 현황을 분석하고 있어요.</em></h1><p>시연 계좌의 보유 종목과 손익, 올해 예상 세금을 계산합니다.</p></div><div className="reference-hero-art" aria-hidden="true"><span className="hero-chart">▥</span><div className="hero-paper"><strong>계산 결과</strong><i/><i/><i/></div></div></section>
    <div className="analysis-grid"><section className="reference-main-card"><h2>분석 진행 상황</h2><ol className="analysis-checks"><li className="done">보유 종목 및 원화 손익 확인</li><li className={comparison ? 'done' : ''}>실현손익과 예상 세금 계산</li><li className={comparison ? 'done' : ''}>매도 조건별 비교 기준 준비</li></ol></section><section className="reference-main-card analysis-outcome"><span className="analysis-meter">{comparison ? '완료' : busy ? '계산 중' : '확인 필요'}</span><h2>{comparison ? '기본 계산이 끝났어요' : error ? '계산을 확인해 주세요' : '계산 중입니다'}</h2><p>{comparison ? `현재 조건에서 최대 ${won(comparison.maxTaxReductionKrw)}의 세금 감소 가능성을 계산했습니다. 다음으로 확인된 기업 자료를 살펴보세요.` : error || '시연 자산의 손익과 사용자 제한을 Java에서 검증하고 있습니다.'}</p></section></div>
    <div className="reference-bottom-action"><button className="button primary" disabled={busy} onClick={comparison ? onContinue : onRetry}>{comparison ? '기업 자료 확인하기' : '다시 계산'} <Icon name="arrow" size={18}/></button></div>
    <p className="reference-footnote">이 단계는 시연 자산의 세금 계산입니다. 다음 화면에서 최신 기업 자료를 조사할 수 있습니다.</p>
  </div>;
}

export function EventExplorer({ portfolio, research, liveResearch, researchBusy, researchError, onResearch, selectedSymbol, onSelectSymbol, onChat, assessment }) {
  const [tab, setTab] = useState('summary');
  const [query, setQuery] = useState('');
  const [kind, setKind] = useState('all');
  const symbols = heldSymbols(portfolio);
  const notes = research?.notes || [];
  const sources = research?.sources || [];
  const visibleSymbols = symbols.filter(item => {
    const itemNote = notes.find(note => note.symbol === item);
    const itemSource = sources.find(source => source.id === itemNote?.sourceId);
    return (kind === 'all' || itemSource?.kind === kind) && `${names[item] || ''} ${item}`.toLowerCase().includes(query.trim().toLowerCase());
  });
  const note = notes.find(item => item.symbol === selectedSymbol);
  const source = sources.find(item => item.id === note?.sourceId);
  const holding = positionFor(portfolio, selectedSymbol);
  const liveSources = assessment?.mode === 'live' ? assessment.research.sources : [];
  const currentResearch = liveResearch?.research;
  return <div className="events-page">
    <section className="reference-hero compact"><div className="reference-hero-copy"><span className="reference-kicker">기업 자료 확인</span><h1>매도 검토 전에<br/><em>보유 기업의 자료를 확인해요.</em></h1><p>확인된 기업 발표의 성장 요인과 위험을 함께 살펴본 뒤, 내 보유 의사를 정해 보세요.</p></div><div className="reference-hero-art" aria-hidden="true"><span className="hero-chart">▥</span><div className="hero-paper"><strong>기업 발표</strong><i/><i/><i/></div></div></section>
    <section className="reference-main-card live-research-panel"><div className="reference-section-head"><div><h2>최신 기업·실적 자료 조사</h2><p>실제 웹 검색을 시작하면 이후 대화와 시나리오에서 같은 결과를 최대 30분간 사용합니다.</p></div><button className="button light" disabled={researchBusy} onClick={onResearch}>{researchBusy ? '조사 중…' : currentResearch ? '최신 자료 다시 확인' : '최신 자료 조사 시작'}</button></div>
      {researchError && <p role="alert" className="error-text">{researchError}</p>}
      {currentResearch ? <><p className="muted">조사 날짜 {liveResearch.researchedAt} · 출처 {currentResearch.sources.length}개 · 실적 일정은 원문에서 다시 확인하세요.</p><div className="live-research-claims">{currentResearch.claims.map((claim, index) => <div key={index}><p>{claim.text}</p>{currentResearch.sources.filter(source => claim.sourceIds.includes(source.id)).map(source => <a key={source.id} href={source.url} target="_blank" rel="noreferrer">{source.title} ↗</a>)}</div>)}</div></> : <p className="event-caveat">최신 자료는 아직 조사하지 않았습니다. 아래는 시연용 과거 발표 자료입니다.</p>}
    </section>
    <div className="event-workspace"><section className="reference-main-card event-list"><div className="reference-section-head"><div><h2>보유 종목별 기업 자료</h2><p>확인된 과거 발표와 보유 현황</p></div></div>
      <input className="event-search" value={query} onChange={event => setQuery(event.target.value)} aria-label="보유 종목 검색" placeholder="종목명 또는 종목코드 검색"/>
      <div className="event-filters" aria-label="자료 종류"><button className={kind === 'all' ? 'active' : ''} onClick={() => setKind('all')}>전체</button><button className={kind === 'company' ? 'active' : ''} onClick={() => setKind('company')}>기업 발표</button><button className={kind === 'filing' ? 'active' : ''} onClick={() => setKind('filing')}>공시</button></div>
      <div className="event-rows">{visibleSymbols.map(item => { const itemNote = notes.find(n => n.symbol === item); const itemSource = sources.find(s => s.id === itemNote?.sourceId); return <button key={item} className={'event-row ' + (selectedSymbol === item ? 'active' : '')} onClick={() => { onSelectSymbol(item); setTab('summary'); }}><StockMark symbol={item}/><span><strong>{names[item] || item}</strong><small>{item}</small></span><span className="event-row-meta">{itemSource ? <>{itemSource.kind === 'filing' ? '공시' : '기업 발표'} <small>{itemSource.publishedAt || '발행일 미확인'}</small></> : '확인된 자료 없음'}</span><b aria-hidden="true">›</b></button>; })}{visibleSymbols.length === 0 && <p className="event-caveat">조건에 맞는 보유 종목이 없습니다.</p>}</div>
      <p className="event-caveat">실적발표 예정일·배당일은 현재 데이터에 없습니다. 확인된 과거 발표일만 표시합니다.</p>
    </section><section className="reference-main-card event-detail"><div className="event-detail-top"><div className="stock-cell"><StockMark symbol={selectedSymbol}/><div><h2>{names[selectedSymbol] || selectedSymbol}</h2><small>{selectedSymbol}</small></div></div><span className="tag">보유 {holding.quantity}주 · {won(holding.value)}</span></div>
      <div className="event-tabs" role="tablist" aria-label="기업 자료 보기"><button role="tab" aria-selected={tab === 'summary'} className={tab === 'summary' ? 'active' : ''} onClick={() => setTab('summary')}>기업 자료 요약</button><button role="tab" aria-selected={tab === 'source'} className={tab === 'source' ? 'active' : ''} onClick={() => setTab('source')}>출처 원문</button></div>
      {tab === 'summary' ? note ? <div className="event-summary"><span className="reference-kicker">확인된 기업 발표 요약</span><p className="event-date">발표 {source?.publishedAt || '발행일 미확인'} · 자료 수집 {source?.collectedAt || '날짜 미확인'}</p><div><strong>성장 참고 요인</strong><p>{note.positive}</p></div><div><strong>함께 볼 위험</strong><p>{note.risk}</p></div><p className="event-relevance">{note.relevance}</p></div> : <p className="muted">이 종목에 연결된 확인 자료가 없습니다.</p>
        : <div className="event-source">{source ? <><h3>{source.title}</h3><p>{source.summary}</p><a href={source.url} target="_blank" rel="noreferrer">원문 확인 ↗</a></> : <p>연결된 출처가 없습니다.</p>}</div>}
      {liveSources.length > 0 && <details className="event-live-sources"><summary>시나리오에 사용한 최신 출처 {liveSources.length}개</summary>{liveSources.map(item => <a key={item.id} href={item.url} target="_blank" rel="noreferrer">{item.title} ↗</a>)}</details>}
    </section></div>
    <section className="event-chat-launch"><div><span className="reference-kicker">WON AI 맞춤 절세 상담</span><h2>어떤 주식을 남기고,<br/><em>어떤 주식을 매도할지 고민되시나요?</em></h2><p>대화를 시작하면 최신 자료를 확인한 뒤, 보유 의사와 매도 범위를 함께 정리합니다.</p><button className="button primary" disabled={researchBusy} onClick={onChat}>{researchBusy ? '최신 자료 조사 중…' : 'AI와 절세 계획 세우기'} <Icon name="arrow" size={18}/></button></div><div className="event-chat-note"><span className="large-chat-orb"><Icon name="spark" size={23}/></span><p>어떤 종목을 오래 보유하고 싶으신가요?<br/>매도하고 싶은 범위도 함께 알려주세요.</p></div></section>
  </div>;
}

export function ScenarioChooser({ comparison, assessment, assessBusy, conditions, selectedId, onSelect, onReport, onEdit, onRetry }) {
  const scenarios = comparison?.scenarios || [];
  const selected = scenarios.find(item => item.id === selectedId) || scenarios[1] || scenarios[0];
  const live = assessment?.mode === 'live';
  const chosenSources = live ? assessment.research.sources.filter(source => (assessment.scenarioSourceIds?.[selected?.id] || []).includes(source.id)) : [];
  return <div className="scenario-choice"><span className="reference-kicker">나에게 맞는 절세 시나리오</span><h2>조건을 반영한 시나리오를 비교해 보세요.</h2><p className="scenario-choice-intro">{assessBusy ? 'Java 계산 결과를 먼저 보여드립니다. AI가 기업 자료를 검색하고 전략을 제안하고 있어요.' : live ? 'AI가 출처가 있는 자료로 전략을 제안했고, Java가 수량과 세액을 검증했습니다.' : assessment?.message || '현재는 Java 계산 기준의 비교 결과입니다.'}</p>
    {assessment?.mode === 'unavailable' && <button className="button light" onClick={onRetry}>AI 자료 조사 다시 시도</button>}
    <div className="reference-scenario-cards">{scenarios.map((scenario, index) => { const sales = scenario.holdings.filter(holding => holding.sellQuantity > 0); const chosen = scenario.id === selected?.id; return <button key={scenario.id} className={'reference-scenario-card ' + (chosen ? 'chosen ' : '') + (live && scenario.id === 'balanced' ? 'recommended' : '')} onClick={() => onSelect(scenario.id)} aria-pressed={chosen}><span className="scenario-card-top"><span>시나리오 {index + 1}</span><span>{scenario.retainedPercent}% 보유</span></span><strong>{scenario.title}</strong><span className="sale-line">{sales.length ? sales.map(holding => `${holding.name} ${holding.sellQuantity}주`).join(' · ') + ' 매도' : '모든 종목 그대로 보유'}</span><span className="saving-label">예상 세금 감소</span><b>{won(scenario.taxReductionKrw)}</b><span className="scenario-card-metrics"><span>올해 예상 세금 <strong>{won(scenario.estimatedTaxKrw)}</strong></span><span>추가 매도 비용 <strong>{won(scenario.saleFeeKrw)}</strong></span><span>실현하는 손익 <strong>{signedWon(scenario.realizedGainKrw)}</strong></span></span>{live && scenario.id === 'balanced' && <em className="recommend-mark">AI 균형안</em>}{scenario.duplicateOf && <small>다른 시나리오와 매도 수량이 같습니다.</small>}</button>; })}</div>
    {selected && <section className="reference-selected-detail"><div><span className="reference-kicker">선택한 안의 근거</span><h3>{selected.title}</h3><p>{selected.reason}</p><p className="muted">{selected.tradeoff}</p>{conditions.cashNeedKrw != null && <p>필요 현금 {won(conditions.cashNeedKrw)} 중 {won(selected.cashKrw)} 확보 · {Number(selected.cashKrw) >= Number(conditions.cashNeedKrw) ? '목표 충족' : '목표 미달'}</p>}{conditions.holdingMonths != null && <p>보유 계획 {conditions.holdingMonths}개월 · {live ? 'AI 전략 제안에 고려' : '기본 계산에는 미반영'}</p>}{chosenSources.length > 0 && <div className="reference-source-links">{chosenSources.map(source => <a key={source.id} href={source.url} target="_blank" rel="noreferrer">{source.title} ↗</a>)}</div>}</div><div className="table-scroll"><table><thead><tr><th>종목</th><th>매도</th><th>계속 보유</th><th>선택 이유</th></tr></thead><tbody>{selected.holdings.map(holding => <tr key={holding.symbol}><td>{holding.name}</td><td>{holding.sellQuantity}주</td><td>{holding.remainingQuantity}주</td><td>{assessment?.stockReasons?.[selected.id]?.[holding.symbol] || holding.reason}</td></tr>)}</tbody></table></div></section>}
    <div className="scenario-choice-actions"><button className="button light" onClick={onEdit}>조건 수정</button><button className="button primary" disabled={!selected || assessBusy} onClick={onReport}>선택한 안으로 보고서 보기 <Icon name="arrow" size={18}/></button></div>
  </div>;
}

export function FinalReport({ comparison, scenario, assessment, conditions, onBack, onChat }) {
  if (!scenario) return null;
  const baseline = Number(comparison.baselineTaxKrw);
  const nextTax = Number(scenario.estimatedTaxKrw);
  const percentage = baseline > 0 ? Math.round((baseline - nextTax) / baseline * 10) / 10 : 0;
  const sold = scenario.holdings.filter(holding => holding.sellQuantity > 0);
  const sources = assessment?.mode === 'live' ? assessment.research.sources.filter(source => (assessment.scenarioSourceIds?.[scenario.id] || []).includes(source.id)) : [];
  return <div className="reference-report"><span className="reference-kicker">최종 보고서</span><h2>{scenario.title} 시나리오</h2><p>현재 시연 자산과 정한 조건에서 예상 세금은 <strong>{won(nextTax)}</strong>, 현재 예상 세금 대비 감소액은 <strong>{won(scenario.taxReductionKrw)}</strong>입니다.</p>
    <div className="report-grid"><section className="reference-main-card"><h3>01 · 시나리오 개요</h3><p>{scenario.reason}</p><div className="report-stats"><div><span>예상 세금 감소</span><strong>{won(scenario.taxReductionKrw)}</strong>{baseline > 0 && <small>{percentage}% 감소</small>}</div><div><span>매도 종목·수량</span><strong>{sold.length ? `${sold.length}개 종목 ${sold.reduce((sum, item) => sum + item.sellQuantity, 0)}주` : '매도 없음'}</strong></div><div><span>주식 보유 비율</span><strong>{scenario.retainedPercent}%</strong></div><div><span>추가 매도 비용</span><strong>{won(scenario.saleFeeKrw)}</strong></div></div></section>
      <section className="reference-main-card"><h3>02 · 세금 효과 비교</h3><div className="tax-compare"><div><span>현재 예상 세금</span><strong>{won(baseline)}</strong></div><div><span>선택한 안의 예상 세금</span><strong>{won(nextTax)}</strong></div></div><div className="report-line"><span>실현하는 원화 손익</span><strong>{signedWon(scenario.realizedGainKrw)}</strong></div><div className="report-line"><span>매도 후 현금</span><strong>{won(scenario.cashKrw)}</strong></div></section>
      <section className="reference-main-card"><h3>03 · 종목별 가상 매도 계획</h3>{conditions.cashNeedKrw != null && <p>필요 현금 {won(conditions.cashNeedKrw)} · 이 안의 예상 현금 {won(scenario.cashKrw)} · {Number(scenario.cashKrw) >= Number(conditions.cashNeedKrw) ? '충족' : '미달'}</p>}{conditions.holdingMonths != null && <p>보유 계획 {conditions.holdingMonths}개월 · {assessment?.mode === 'live' ? 'AI 전략 제안에 고려' : '기본 계산에는 미반영'}</p>}<div className="table-scroll"><table><thead><tr><th>종목</th><th>보유</th><th>가상 매도</th><th>계속 보유</th><th>판단 근거</th></tr></thead><tbody>{scenario.holdings.map(holding => <tr key={holding.symbol}><td>{holding.name}</td><td>{holding.quantity}주</td><td>{holding.sellQuantity}주</td><td>{holding.remainingQuantity}주</td><td>{assessment?.stockReasons?.[scenario.id]?.[holding.symbol] || holding.reason}</td></tr>)}</tbody></table></div></section>
      <section className="reference-main-card"><h3>꼭 확인해 주세요</h3><p>{scenario.tradeoff}</p><p>가상 계좌·가격·환율을 사용한 시연입니다. 실제 주문이나 세금 신고는 실행되지 않습니다. 향후 실적 일정과 기업 자료는 원문에서 다시 확인하세요.</p>{sources.length > 0 && <div className="reference-source-links">{sources.map(source => <a key={source.id} href={source.url} target="_blank" rel="noreferrer">판단 출처 · {source.title} ↗</a>)}</div>}</section></div>
    <div className="report-actions"><button className="button light" onClick={onBack}>시나리오 다시 비교</button><button className="button light" onClick={() => window.print()}>인쇄 · PDF로 저장</button><button className="button primary" onClick={onChat}>상담 계속하기 <Icon name="arrow" size={18}/></button></div>
  </div>;
}
