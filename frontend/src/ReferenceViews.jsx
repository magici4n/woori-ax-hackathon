import React from 'react';
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
    <div className="reference-bottom-action"><button className="button primary" disabled={busy} onClick={onStart}>{busy ? '분석하고 있어요…' : '내 양도소득세 줄이기'} <Icon name="arrow" size={19}/></button></div>
  </div>;
}

export function AnalysisStep({ portfolio, comparison, calculationBusy, calculationError, liveResearch, researchBusy, researchError, onContinue, onRetry, onContinueWithoutResearch }) {
  const working = calculationBusy || researchBusy;
  const analyzedCount = liveResearch?.articles?.filter(article => article.summary).length || 0;
  const stockAnalysisCount = liveResearch?.stockAnalyses?.length || 0;
  const stockCount = new Set(portfolio?.positions?.map(position => position.lot.symbol) || []).size;
  const failed = !working && Boolean(calculationError || researchError);
  const warnings = liveResearch?.warnings || [];
  const complete = Boolean(comparison && liveResearch && analyzedCount === liveResearch.articles.length && analyzedCount > 0 && stockAnalysisCount === stockCount && stockCount > 0 && warnings.length === 0 && !working && !failed);
  const partial = Boolean(comparison && liveResearch && !complete && !working && !failed);
  const status = failed ? '확인 필요' : complete ? '완료' : partial ? '일부 완료' : '진행 중';
  const heading = failed ? '분석을 완료하지 못했어요' : complete ? '계산과 기업 자료 조사가 끝났어요' : partial ? '원문 자료까지 확인했어요' : '계산과 기업 자료를 확인하고 있어요';
  const detail = failed ? [calculationError && `세금 계산: ${calculationError}`, researchError && `기업 자료 조사: ${researchError}`].filter(Boolean).join(' · ')
    : complete
    ? `최대 ${won(comparison.maxTaxReductionKrw)}의 세금 감소 가능성을 계산하고, 기사 ${analyzedCount}건과 종목 ${stockAnalysisCount}개를 AI가 분석했습니다.`
    : partial ? `기사 ${analyzedCount}/${liveResearch.articles.length}건, 종목 종합 분석 ${stockAnalysisCount}/${stockCount}개가 완료됐습니다. 나머지는 원문을 확인해 주세요.`
      : comparison ? `세금 계산이 끝났어요. 최대 ${won(comparison.maxTaxReductionKrw)}의 감소 가능성을 확인했고, 기사 검색과 AI 분석을 계속하고 있습니다.`
          : '보유 종목의 세금을 계산하고 실제 기사·공시를 검색해 AI가 내용을 정리하고 있습니다.';
  return <div className="analysis-page"><section className="reference-hero compact"><div className="reference-hero-copy"><span className="reference-kicker">AI 사전 분석</span>{complete || partial ? <h1>최대 절세 가능액<br/><em>{won(comparison.maxTaxReductionKrw)}</em></h1> : <h1>고객님의<br/><em>투자 현황을 분석하고 있어요.</em></h1>}<p>{complete || partial ? '세금 계산과 기업 자료 조사를 마쳤습니다. 보유 기업의 이벤트와 기사 내용을 먼저 살펴보세요.' : '시연 계좌의 세금을 계산하면서 보유 기업의 기사·공시를 조사하고 있습니다.'}</p></div><div className="reference-hero-art" aria-hidden="true"><span className="hero-chart">▥</span><div className="hero-paper"><strong>{complete ? '분석 완료' : partial ? '일부 완료' : 'AI 분석 중'}</strong><i/><i/><i/></div></div></section>
    <div className="analysis-grid"><section className="reference-main-card"><h2>분석 진행 상황</h2><ol className="analysis-checks"><li className="done">보유 종목 및 원화 손익 확인</li><li className={comparison ? 'done' : ''}>실현손익과 예상 세금 계산</li><li className={comparison ? 'done' : ''}>매도 조건별 비교 기준 준비</li><li className={liveResearch ? 'done' : ''}>실제 기사·공시 검색</li><li className={liveResearch && analyzedCount === liveResearch.articles.length && analyzedCount > 0 ? 'done' : ''}>기사별 AI 분석 {liveResearch ? `${analyzedCount}/${liveResearch.articles.length}건` : ''}</li><li className={complete ? 'done' : ''}>보유 종목 종합 분석 {liveResearch ? `${stockAnalysisCount}/${stockCount}개` : ''}</li></ol></section><section className="reference-main-card analysis-outcome" role="status" aria-live="polite" aria-busy={working}><span className={'analysis-meter ' + (working ? 'spinning' : failed || partial ? 'needs-attention' : '')}>{status}</span><h2>{heading}</h2><p>{detail}</p>{warnings.length > 0 && <div role="alert" className="error-text"><strong>완료하지 못한 단계</strong><ul>{warnings.map((warning, index) => <li key={index}>{warning}</li>)}</ul></div>}</section></div>
    <div className="reference-bottom-action">{complete || partial ? <button className="button primary" onClick={onContinue}>기업 자료 확인하기 <Icon name="arrow" size={18}/></button> : null}{!complete && <button className={partial ? 'button light' : 'button primary'} disabled={working} onClick={onRetry}>{working ? '자료를 준비하고 있어요…' : '기사 다시 검색·분석'} <Icon name="arrow" size={18}/></button>}{failed && comparison && !liveResearch && <button className="button light analysis-skip" onClick={onContinueWithoutResearch}>자료 없이 계속</button>}</div>
    <p className="reference-footnote">검색 자료와 AI 분석은 출처 원문을 함께 확인해 주세요. 이 화면의 세금 계산은 시연 계좌 기준입니다.</p>
  </div>;
}

export function QuestionLanding({ portfolio, conditions }) {
  const preferred = heldSymbols(portfolio).filter(symbol => conditions.symbols[symbol]?.excluded);
  return <div className="question-landing"><section className="reference-hero compact"><div className="reference-hero-copy"><span className="reference-kicker">WON AI 맞춤 종목 상담</span><h1>어떤 주식을 남기고,<br/><em>어떤 주식을 매도할지 고민되시나요?</em></h1><p>AI와 대화하며 보유 의사와 필요한 현금, 매도 가능한 범위를 정리하고 나에게 맞는 절세 시나리오를 살펴보세요.</p></div><div className="reference-hero-art" aria-hidden="true"><span className="hero-chart">✦</span><div className="hero-paper"><strong>AI 상담</strong><i/><i/><i/></div></div></section><div className="question-landing-cards"><section className="reference-main-card"><h2>현재 보유 중인 종목</h2><div className="question-stock-list">{heldSymbols(portfolio).map(symbol => <span key={symbol}><StockMark symbol={symbol}/>{names[symbol] || symbol}</span>)}</div></section><section className="reference-main-card"><h2>지금까지 정한 조건</h2><p>{preferred.length ? `${preferred.map(symbol => names[symbol] || symbol).join(', ')} 매도 제외` : '아직 매도 제외 종목을 정하지 않았어요.'}</p><p>{conditions.cashNeedKrw != null ? `필요 현금 ${won(conditions.cashNeedKrw)}` : '필요 현금은 대화 중에 알려주셔도 됩니다.'}</p></section></div></div>;
}

export function ScenarioChooser({ comparison, assessment, assessBusy, conditions, selectedId, onSelect, onReport, onEdit, onRetry }) {
  const scenarios = comparison?.scenarios || [];
  const selected = scenarios.find(item => item.id === selectedId) || scenarios[1] || scenarios[0];
  const live = assessment?.mode === 'live';
  const chosenSources = live ? assessment.research.sources.filter(source => (assessment.scenarioSourceIds?.[selected?.id] || []).includes(source.id)) : [];
  return <div className="scenario-choice"><span className="reference-kicker">나에게 맞는 절세 시나리오</span><h2>조건을 반영한 시나리오를 비교해 보세요.</h2><p className="scenario-choice-intro">{assessBusy ? 'Java 계산 결과를 먼저 보여드립니다. AI가 기업 자료를 검색하고 전략을 제안하고 있어요.' : live ? 'AI가 출처가 있는 자료로 전략을 제안했고, Java가 수량과 세액을 검증했습니다.' : assessment?.message || '현재는 Java 계산 기준의 비교 결과입니다.'}</p>
    {assessment?.mode === 'unavailable' && <button className="button light" onClick={onRetry}>AI 자료 조사 다시 시도</button>}
    <div className="reference-scenario-cards">{scenarios.map((scenario, index) => { const sales = scenario.holdings.filter(holding => holding.sellQuantity > 0); const chosen = scenario.id === selected?.id; return <button key={scenario.id} className={'reference-scenario-card ' + (chosen ? 'chosen ' : '') + (live && scenario.id === 'balanced' ? 'recommended' : '')} onClick={() => onSelect(scenario.id)} aria-pressed={chosen}><span className="scenario-card-top"><span>시나리오 {index + 1}</span><span>{scenario.retainedPercent}% 보유</span></span><strong>{scenario.title}</strong><span className="sale-line">{sales.length ? sales.map(holding => `${holding.name} ${holding.sellQuantity}주`).join(' · ') + ' 매도' : '모든 종목 그대로 보유'}</span><span className="saving-label">예상 세금 감소</span><b>{won(scenario.taxReductionKrw)}</b><span className="scenario-card-metrics"><span>올해 예상 세금 <strong>{won(scenario.estimatedTaxKrw)}</strong></span><span>추가 매도 비용 <strong>{won(scenario.saleFeeKrw)}</strong></span><span>실현하는 손익 <strong>{signedWon(scenario.realizedGainKrw)}</strong></span></span>{live && scenario.id === 'balanced' && <em className="recommend-mark">★ AI 추천</em>}{scenario.duplicateOf && <small>다른 시나리오와 매도 수량이 같습니다.</small>}</button>; })}</div>
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
  return <div className="reference-report"><span className="reference-kicker">최종 보고서</span><h2>{scenario.title} 시나리오로<br/>올해 예상 세금 <em>{won(scenario.taxReductionKrw)}</em>을 줄일 수 있어요.</h2><p>현재 시연 자산과 정한 조건을 반영한 예상 결과입니다. 적용 시 예상 세금은 <strong>{won(nextTax)}</strong>입니다.</p>
    <div className="report-grid"><section className="reference-main-card"><h3>01 · 시나리오 개요</h3><p>{scenario.reason}</p><div className="report-stats"><div><span>예상 세금 감소</span><strong>{won(scenario.taxReductionKrw)}</strong>{baseline > 0 && <small>{percentage}% 감소</small>}</div><div><span>매도 종목·수량</span><strong>{sold.length ? `${sold.length}개 종목 ${sold.reduce((sum, item) => sum + item.sellQuantity, 0)}주` : '매도 없음'}</strong></div><div><span>주식 보유 비율</span><strong>{scenario.retainedPercent}%</strong></div><div><span>추가 매도 비용</span><strong>{won(scenario.saleFeeKrw)}</strong></div></div></section>
      <section className="reference-main-card"><h3>02 · 세금 효과 비교</h3><div className="tax-compare"><div><span>현재 예상 세금</span><strong>{won(baseline)}</strong></div><div><span>선택한 안의 예상 세금</span><strong>{won(nextTax)}</strong></div></div><div className="report-line"><span>실현하는 원화 손익</span><strong>{signedWon(scenario.realizedGainKrw)}</strong></div><div className="report-line"><span>매도 후 현금</span><strong>{won(scenario.cashKrw)}</strong></div></section>
      <section className="reference-main-card"><h3>03 · 종목별 가상 매도 계획</h3>{conditions.cashNeedKrw != null && <p>필요 현금 {won(conditions.cashNeedKrw)} · 이 안의 예상 현금 {won(scenario.cashKrw)} · {Number(scenario.cashKrw) >= Number(conditions.cashNeedKrw) ? '충족' : '미달'}</p>}{conditions.holdingMonths != null && <p>보유 계획 {conditions.holdingMonths}개월 · {assessment?.mode === 'live' ? 'AI 전략 제안에 고려' : '기본 계산에는 미반영'}</p>}<div className="table-scroll"><table><thead><tr><th>종목</th><th>보유</th><th>가상 매도</th><th>계속 보유</th><th>판단 근거</th></tr></thead><tbody>{scenario.holdings.map(holding => <tr key={holding.symbol}><td>{holding.name}</td><td>{holding.quantity}주</td><td>{holding.sellQuantity}주</td><td>{holding.remainingQuantity}주</td><td>{assessment?.stockReasons?.[scenario.id]?.[holding.symbol] || holding.reason}</td></tr>)}</tbody></table></div></section>
      <section className="reference-main-card report-side"><div className="report-side-block"><h3>한눈에 보는 핵심 요약</h3><p>✓ 올해 예상 세금 {won(scenario.taxReductionKrw)} 감소</p><p>✓ {sold.length ? `${sold.length}개 종목 ${sold.reduce((sum, item) => sum + item.sellQuantity, 0)}주 가상 매도` : '현재 보유 수량 유지'}</p><p>✓ 주식 {scenario.retainedPercent}% 보유</p></div><div className="report-side-block"><h3>{assessment?.mode === 'live' ? 'AI가 제안하는 이후 검토' : '이후 검토 단계'}</h3><p>① 선택한 종목과 수량을 다시 확인하세요.</p><p>② 올해 실현손익과 세금 계산 기준을 확인하세요.</p><p>③ 기업 발표와 가격 변화를 원문에서 재확인하세요.</p></div><div className="report-side-block"><h3>꼭 확인해 주세요</h3><p>{scenario.tradeoff}</p><p>가상 계좌·가격·환율을 사용한 시연입니다. 실제 주문이나 세금 신고는 실행되지 않습니다. 향후 실적 일정과 기업 자료는 원문에서 다시 확인하세요.</p>{sources.length > 0 && <div className="reference-source-links">{sources.map(source => <a key={source.id} href={source.url} target="_blank" rel="noreferrer">판단 출처 · {source.title} ↗</a>)}</div>}</div></section></div>
    <div className="report-actions"><button className="button light" onClick={onBack}>시나리오 다시 비교</button><button className="button light" onClick={() => window.print()}>인쇄 · PDF로 저장</button><button className="button primary" onClick={onChat}>상담 계속하기 <Icon name="arrow" size={18}/></button></div>
  </div>;
}
