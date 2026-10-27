import React, { useEffect, useRef, useState } from 'react';
import { ServiceHeader, JourneyNav } from './ServiceLayout.jsx';
import { AssetDashboard, AnalysisStep, QuestionLanding, ScenarioChooser, FinalReport } from './ReferenceViews.jsx';
import { EventExplorer } from './LiveEventExplorer.jsx';
import { api, names, emptyConditions, Icon, StockMark, ConditionText, ManualForm } from './ui.jsx';

function ConditionSummary({ conditions, emptyLabel = '추가 매도 제한 없음' }) {
  const entries = Object.entries(conditions.symbols).filter(([, condition]) => condition.excluded || condition.maxShares != null || condition.maxPercent != null);
  return <div className="flow-condition-list">{entries.map(([symbol, condition]) => <span className="condition-chip" key={symbol}><ConditionText symbol={symbol} condition={condition}/></span>)}
    {conditions.targetReductionKrw != null && <span className="condition-chip">희망 감소액 {Number(conditions.targetReductionKrw).toLocaleString()}원</span>}
    {conditions.cashNeedKrw != null && <span className="condition-chip">필요 현금 {Number(conditions.cashNeedKrw).toLocaleString()}원</span>}
    {conditions.holdingMonths != null && <span className="condition-chip">보유 계획 {conditions.holdingMonths}개월</span>}
    {!entries.length && conditions.targetReductionKrw == null && conditions.cashNeedKrw == null && conditions.holdingMonths == null && <p className="muted">{emptyLabel}</p>}
  </div>;
}

export default function App() {
  const [portfolio, setPortfolio] = useState(null), [loadError, setLoadError] = useState('');
  const [stage, setStage] = useState('assets'), [symbol, setSymbol] = useState('TSLA');
  const [conditions, setConditions] = useState(emptyConditions), [draft, setDraft] = useState(emptyConditions);
  const [decisionPriority, setDecisionPriority] = useState('BALANCED');
  const [comparison, setComparison] = useState(null), [selectedId, setSelectedId] = useState('balanced');
  const [calculating, setCalculating] = useState(false), [calculationError, setCalculationError] = useState('');
  const [messages, setMessages] = useState([]), [message, setMessage] = useState(''), [chatBusy, setChatBusy] = useState(false);
  const [pendingQuestion, setPendingQuestion] = useState(null), [needsClarification, setNeedsClarification] = useState(false), [manualOpen, setManualOpen] = useState(false);
  const [assessment, setAssessment] = useState(null), [assessBusy, setAssessBusy] = useState(false);
  const [liveResearch, setLiveResearch] = useState(null), [researchBusy, setResearchBusy] = useState(false), [researchError, setResearchError] = useState(''), [researchSkipped, setResearchSkipped] = useState(false);
  const conversationId = useRef(null), requestVersion = useRef(0), assessmentVersion = useRef(0), simulationVersion = useRef(0), analysisRunning = useRef(false), messagesPane = useRef(null), dialogHeading = useRef(null);
  const quantity = portfolio ? portfolio.positions.filter(position => position.lot.symbol === symbol).reduce((sum, position) => sum + position.lot.quantity, 0) : 0;
  const hasDraft = JSON.stringify(draft) !== JSON.stringify(conditions);
  const canOfferScenario = !chatBusy && !needsClarification && messages.at(-1)?.role === 'assistant' && messages.at(-1)?.mode !== 'unavailable';
  const selectedScenario = comparison?.scenarios.find(scenario => scenario.id === selectedId) || comparison?.scenarios[1] || comparison?.scenarios[0];
  function load() {
    setLoadError(''); api('/portfolio').then(setPortfolio).catch(error => setLoadError(error.message));
  }
  useEffect(load, []);
  useEffect(() => { if (messagesPane.current) messagesPane.current.scrollTop = messagesPane.current.scrollHeight; }, [messages, chatBusy, canOfferScenario, pendingQuestion]);
  useEffect(() => { dialogHeading.current?.focus({ preventScroll: true }); }, [stage]);
  useEffect(() => { if (!['conditions', 'scenarios', 'report'].includes(stage)) return; const onKey = event => { if (event.key === 'Escape') setStage('events'); }; window.addEventListener('keydown', onKey); return () => window.removeEventListener('keydown', onKey); }, [stage]);
  function navigate(next) {
    if (['events', 'conditions', 'scenarios', 'report'].includes(next) && !comparison) next = 'assets';
    setStage(next); window.history.replaceState(null, '', '#' + next);
    if (next === 'assets' || next === 'analysis' || next === 'events') window.scrollTo({ top: 0, behavior: 'instant' });
  }
  function resetConversation(next = conditions) {
    const id = conversationId.current; conversationId.current = null; requestVersion.current++;
    setMessages([]); setMessage(''); setPendingQuestion(null); setNeedsClarification(false); setDraft(next);
    if (id) api('/conversations/' + id, undefined, undefined, 'DELETE').catch(() => {});
  }
  async function startAnalysis(retry = false) {
    if (analysisRunning.current) return;
    analysisRunning.current = true;
    const previousComparison = retry ? comparison : null;
    setStage('analysis'); setResearchSkipped(false);
    if (!retry) { setComparison(null); setLiveResearch(null); }
    setCalculationError(''); setResearchError('');
    setCalculating(!previousComparison); setResearchBusy(true);
    try {
      await Promise.allSettled([
        (previousComparison ? Promise.resolve(previousComparison) : api('/simulation', { conditions }))
          .then(result => { setComparison(result); setConditions(result.conditions); setDraft(result.conditions); })
          .catch(error => setCalculationError(error.message))
          .finally(() => setCalculating(false)),
        api(retry ? '/research/live?refreshSearch=true' : '/research/live', {})
          .then(setLiveResearch)
          .catch(error => setResearchError(error.message))
          .finally(() => setResearchBusy(false)),
      ]);
    } finally {
      analysisRunning.current = false;
    }
  }
  async function fetchResearch(retryAnalysis = false, refreshSearch = false) {
    if (researchBusy) return;
    setResearchBusy(true); setResearchError('');
    try { setLiveResearch(await api(refreshSearch ? '/research/live?refreshSearch=true' : retryAnalysis ? '/research/live?retryAnalysis=true' : '/research/live', {})); setResearchSkipped(false); }
    catch (error) { setResearchError(error.message); }
    finally { setResearchBusy(false); }
  }
  async function assess(next = conditions, simulationRequestVersion = simulationVersion.current, priority = decisionPriority) {
    const version = ++assessmentVersion.current; setAssessBusy(true); setAssessment(null);
    try {
      const result = await api('/simulation/assess', { conditions: next, decisionPriority: priority });
      if (version === assessmentVersion.current && simulationRequestVersion === simulationVersion.current) {
        setAssessment(result);
        if (result.mode === 'live') setComparison(result.comparison);
      }
    } catch (error) {
      if (version === assessmentVersion.current && simulationRequestVersion === simulationVersion.current) setAssessment({ mode: 'unavailable', message: error.message });
    } finally { if (version === assessmentVersion.current) setAssessBusy(false); }
  }
  async function runSimulation(next = draft, priority = decisionPriority) {
    if (calculating || chatBusy) return;
    const version = ++simulationVersion.current; ++assessmentVersion.current;
    setCalculating(true); setCalculationError(''); setAssessment(null); setAssessBusy(false); setSelectedId('balanced');
    navigate('scenarios');
    try {
      const result = await api('/simulation', { conditions: next });
      if (version !== simulationVersion.current) return;
      setComparison(result); setConditions(result.conditions); setDraft(result.conditions);
      setPendingQuestion(null); setNeedsClarification(false); setManualOpen(false);
      assess(result.conditions, version, priority);
    } catch (error) { if (version === simulationVersion.current) setCalculationError(error.message); }
    finally { if (version === simulationVersion.current) setCalculating(false); }
  }
  async function sendText(text) {
    text = text.trim(); if (!text || chatBusy) return;
    const version = ++requestVersion.current; setMessage(''); setChatBusy(true);
    setMessages(previous => [...previous.slice(-19), { role: 'user', text }]);
    try {
      const response = await api('/conditions/extract', { message: text, existing: draft, selectedSymbol: symbol, conversationId: conversationId.current });
      if (version !== requestVersion.current) return;
      conversationId.current = response.conversationId; setPendingQuestion(response.pendingQuestion || null);
      setNeedsClarification(response.questions.length > 0);
      setMessages(previous => [...previous, { role: 'assistant', mode: response.mode, text: [response.message, ...response.questions].join('\n') }]);
      if (response.canApply) setDraft(response.conditions);
    } catch (error) { if (version === requestVersion.current) setMessages(previous => [...previous, { role: 'assistant', mode: 'unavailable', text: error.message }]); }
    finally { if (version === requestVersion.current) setChatBusy(false); }
  }
  if (!portfolio) return <div className="loading-screen"><span className="brand-circle">W</span><h1>해외주식 절세 시나리오</h1>{loadError ? <><p role="alert">시연 자산을 불러오지 못했어요. 백엔드 실행 상태를 확인해 주세요.</p><small>{loadError}</small><button className="button primary" onClick={load}>다시 연결</button></> : <p>시연 자산을 불러오고 있어요…</p>}</div>;
  const modeLabel = { openai: 'AI 도우미', confirmation: '조건 확인', clarification: '함께 정해요', guidance: '이용 안내', unavailable: '연결 안내' };
  const modal = ['conditions', 'scenarios', 'report'].includes(stage);
  return <div className={'flow-app reference-app flow-stage-' + stage}>
    <ServiceHeader onNavigate={navigate}/>
    <main className="flow-main reference-main" id="main-content">
      <div className="reference-breadcrumb">⌂ <span>›</span> 투자 <span>›</span> 해외주식 양도소득세{stage !== 'assets' && <><span>›</span> {stage === 'analysis' ? '내 계좌 분석' : stage === 'events' ? '이벤트 분석' : 'AI 맞춤 절세'}</>}</div>
      {stage !== 'assets' && <JourneyNav active={stage} onNavigate={navigate} canCompare={Boolean(comparison && (liveResearch || researchSkipped)) && !calculating && !researchBusy}/>}
      {stage === 'assets' && <AssetDashboard portfolio={portfolio} onStart={() => startAnalysis()} busy={calculating || researchBusy}/>}
      {stage === 'analysis' && <AnalysisStep portfolio={portfolio} comparison={comparison} calculationBusy={calculating} calculationError={calculationError} liveResearch={liveResearch} researchBusy={researchBusy} researchError={researchError} onContinue={() => navigate('events')} onRetry={() => startAnalysis(true)} onContinueWithoutResearch={() => { setResearchSkipped(true); navigate('events'); }}/>}
      {stage === 'events' && <EventExplorer
        portfolio={portfolio} liveResearch={liveResearch} researchBusy={researchBusy} researchError={researchError}
        onResearch={() => fetchResearch(false, true)} selectedSymbol={symbol}
        onSelectSymbol={next => { resetConversation(draft); setSymbol(next); }}
        onChat={async () => { if (!liveResearch) await fetchResearch(); navigate('conditions'); }}/>
      }
      {['conditions', 'scenarios', 'report'].includes(stage) && <QuestionLanding portfolio={portfolio} conditions={draft}/>}
      <div className="demo-banner flow-demo-note"><Icon name="info" size={17}/><span>가상 계좌·가격·환율을 사용하는 시연입니다. 실제 거래나 세금 신고는 실행하지 않습니다.</span></div>
      <footer className="service-footer"><span>우리투자증권 · 해커톤 프로토타입</span><button onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}>↑ TOP</button></footer>
    </main>
    {modal && <div className="reference-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) navigate('events'); }}><section className={'reference-modal reference-modal-' + stage} role="dialog" aria-modal="true" aria-label={stage === 'conditions' ? 'AI 맞춤 절세 상담' : stage === 'scenarios' ? '절세 시나리오 비교' : '최종 보고서'}>
      <header className="reference-modal-head"><span className="reference-ai-orb">AI</span><div><strong>WON AI 맞춤 절세 상담 <small>Beta</small></strong><p>{stage === 'conditions' ? '내 보유 종목과 조건을 바탕으로 절세 시나리오를 함께 만들어요.' : stage === 'scenarios' ? '조건을 반영한 시나리오의 세금 효과를 비교하세요.' : '선택한 시나리오의 계산 결과와 근거를 확인하세요.'}</p></div><button className="reference-close" aria-label="창 닫기" onClick={() => navigate('events')}><Icon name="close" size={24}/></button></header>
      {stage === 'conditions' && <div className="reference-chat-layout"><section className="conversation-card reference-chat-card" aria-label="투자 의사 대화"><div className="reference-chat-title"><h2 ref={dialogHeading} tabIndex={-1}>몇 가지를 함께 확인할게요.</h2><button className="new-chat-button" disabled={chatBusy} onClick={() => resetConversation()}>새 대화</button></div>
        <div className="flow-chat-history" ref={messagesPane} aria-live="polite" aria-busy={chatBusy}><div className="flow-chat-message assistant"><span className="flow-chat-avatar">AI</span><div><small>AI 도우미</small><p>보유 종목과 확인된 기업 자료를 참고해 조건을 정리할게요. 오래 보유하고 싶은 종목이나 매도 가능한 범위를 말씀해 주세요.</p></div></div>
          {!messages.length && <div className="conversation-suggestions"><p>이렇게 이야기해 보세요</p><button disabled={chatBusy} onClick={() => sendText('테슬라는 장기투자 중이라 팔 생각 없어.')}>테슬라는 계속 보유하고 싶어요</button><button disabled={chatBusy} onClick={() => sendText('애플은 절반까지만 팔 수 있어.')}>애플은 절반까지만 팔래요</button><button disabled={chatBusy} onClick={() => sendText('절세보다 보유 기회가 더 중요해.')}>보유 기회를 우선하고 싶어요</button></div>}
          {messages.map((item, index) => <div key={index} className={'flow-chat-message ' + item.role}>{item.role === 'assistant' && <span className="flow-chat-avatar">AI</span>}<div>{item.role === 'assistant' && <small>{modeLabel[item.mode] || 'AI 도우미'}</small>}<p>{item.text}</p></div></div>)}
          {chatBusy && <div className="chat-working" role="status"><span/><span/><span/>투자 의사를 정리하고 있어요</div>}
          {pendingQuestion && <div className="question-actions"><button disabled={chatBusy} onClick={() => sendText('네')}>네, 적용해 주세요</button><button disabled={chatBusy} onClick={() => sendText('아니요')}>아니요</button></div>}
          {canOfferScenario && <div className="in-chat-proposal"><div><strong>{hasDraft ? '정리한 조건으로 시나리오를 볼까요?' : '현재 조건으로 시나리오를 볼까요?'}</strong><ConditionSummary conditions={draft}/></div><button className="button primary" onClick={() => runSimulation(draft)}>시나리오 보기 <Icon name="arrow" size={16}/></button></div>}
        </div><div className="flow-chat-composer"><form onSubmit={event => { event.preventDefault(); sendText(message); }}><textarea aria-label="투자 의사 입력" rows={2} maxLength={1000} value={message} disabled={chatBusy} placeholder="궁금한 점을 자유롭게 질문해 주세요." onChange={event => setMessage(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); sendText(message); } }}/><button type="submit" aria-label="메시지 전송" disabled={chatBusy || !message.trim()}><Icon name="send" size={21}/></button></form><p>Enter 전송 · Shift+Enter 줄바꿈 · 조건을 확정하기 전에는 기존 결과가 바뀌지 않아요.</p></div></section>
        <aside className="reference-chat-context"><section className="reference-main-card"><h3>보유 종목 · 계좌 정보 요약</h3><div className="chat-context-numbers"><div><span>해외주식 평가금액</span><strong>{Number(portfolio.marketValueKrw).toLocaleString()}원</strong></div><div><span>예상 양도소득세</span><strong>{Number(portfolio.estimatedTaxKrw).toLocaleString()}원</strong></div><div><span>보유 종목</span><strong>{new Set(portfolio.positions.map(position => position.lot.symbol)).size}개</strong></div></div></section>
          <section className="reference-main-card"><h3>확인한 기업 자료</h3>{liveResearch ? <><p className="muted">실시간 조사 {liveResearch.researchedAt} · 출처 {liveResearch.research.sources.length}개</p>{liveResearch.articles.slice(0, 3).map(article => <div className="chat-context-source" key={`${article.symbol}-${article.sourceId}`}><StockMark symbol={article.symbol}/><span>{names[article.symbol]}<small>{article.summary || article.title}</small></span></div>)}</> : <><p className="muted">아직 실시간 조사를 하지 않았어요.</p><button className="button light" disabled={researchBusy} onClick={() => fetchResearch()}>{researchBusy ? '조사 중…' : '최신 자료 조사'}</button>{researchError && <small role="alert">{researchError}</small>}</>}</section>
          <section className="reference-main-card"><h3>AI와 함께 설정할 조건</h3><ConditionSummary conditions={draft}/><div className="chat-context-priority"><label>판단 우선순위<select value={decisionPriority} disabled={chatBusy} onChange={event => setDecisionPriority(event.target.value)}><option value="BALANCED">세금·보유 균형</option><option value="HOLD">보유 기회 우선</option><option value="TAX">올해 절세 우선</option></select></label><label>대화 기준 종목<select value={symbol} disabled={chatBusy} onChange={event => { resetConversation(draft); setSymbol(event.target.value); }}>{Object.entries(names).map(([code, name]) => <option key={code} value={code}>{name} ({code})</option>)}</select></label><p>“이 종목”은 {names[symbol]} 합산 {quantity}주를 뜻합니다.</p></div><button className="button primary wide" disabled={chatBusy || needsClarification || calculating} onClick={() => runSimulation(draft)}>{comparison ? '이 조건으로 다시 계산' : '시나리오 보기'}</button></section>
          <details className="reference-main-card reference-direct-editor" open={manualOpen} onToggle={event => setManualOpen(event.currentTarget.open)}><summary>직접 조건 수정</summary><ManualForm conditions={draft} positions={portfolio.positions} disabled={chatBusy} onApply={next => { resetConversation(next); runSimulation(next); }}/></details>
        </aside></div>}
      {stage === 'scenarios' && <div className="reference-modal-content" ref={dialogHeading} tabIndex={-1}>{calculating ? <div className="calculating-result" role="status">사용자 조건으로 세금과 시나리오를 계산하고 있어요…</div> : calculationError ? <div className="error-box" role="alert">{calculationError}<button className="button light" onClick={() => runSimulation(draft)}>다시 계산</button></div> : comparison && <ScenarioChooser comparison={comparison} assessment={assessment} assessBusy={assessBusy} conditions={draft} selectedId={selectedId} onSelect={setSelectedId} onReport={() => navigate('report')} onEdit={() => navigate('conditions')} onRetry={() => assess()}/>}</div>}
      {stage === 'report' && <div className="reference-modal-content" ref={dialogHeading} tabIndex={-1}><FinalReport comparison={comparison} scenario={selectedScenario} assessment={assessment} conditions={draft} onBack={() => navigate('scenarios')} onChat={() => navigate('conditions')}/></div>}
    </section></div>}
  </div>;
}
