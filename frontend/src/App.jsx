import React, { useEffect, useRef, useState } from 'react';
import { ServiceHeader, JourneyNav } from './ServiceLayout.jsx';
import { AssetOverview, PortfolioResults } from './PortfolioResults.jsx';
import { api, names, emptyConditions, Icon, StockMark, ConditionText, ManualForm } from './ui.jsx';

function ConditionSummary({ conditions, emptyLabel = '추가 매도 제한 없음' }) {
  const entries = Object.entries(conditions.symbols).filter(([, c]) => c.excluded || c.maxShares != null || c.maxPercent != null);
  return <div className="flow-condition-list">{entries.map(([symbol, condition]) => <span className="condition-chip" key={symbol}><ConditionText symbol={symbol} condition={condition}/></span>)}
    {conditions.targetReductionKrw != null && <span className="condition-chip">희망 감소액 {Number(conditions.targetReductionKrw).toLocaleString()}원</span>}
    {!entries.length && conditions.targetReductionKrw == null && <p className="muted">{emptyLabel}</p>}
  </div>;
}

export default function App() {
  const [portfolio, setPortfolio] = useState(null), [loadError, setLoadError] = useState(''), [usage, setUsage] = useState(null);
  const [stage, setStage] = useState('assets'), [symbol, setSymbol] = useState('TSLA');
  const [conditions, setConditions] = useState(emptyConditions), [draft, setDraft] = useState(emptyConditions);
  const [comparison, setComparison] = useState(null), [previous, setPrevious] = useState(null);
  const [calculating, setCalculating] = useState(false), [calculationError, setCalculationError] = useState('');
  const [messages, setMessages] = useState([]), [message, setMessage] = useState(''), [chatBusy, setChatBusy] = useState(false);
  const [pendingQuestion, setPendingQuestion] = useState(null), [needsClarification, setNeedsClarification] = useState(false), [manualOpen, setManualOpen] = useState(false);
  const [explanation, setExplanation] = useState(null), [explainBusy, setExplainBusy] = useState(false), [research, setResearch] = useState(null);
  const conversationId = useRef(null), requestVersion = useRef(0), explanationVersion = useRef(0), simulationVersion = useRef(0), messagesPane = useRef(null), stageHeading = useRef(null);
  const quantity = portfolio ? portfolio.positions.filter(p => p.lot.symbol === symbol).reduce((sum, p) => sum + p.lot.quantity, 0) : 0;
  const hasDraft = JSON.stringify(draft) !== JSON.stringify(conditions);
  const canRerun = hasDraft && !chatBusy && !needsClarification && messages.at(-1)?.role === 'assistant' && messages.at(-1)?.mode !== 'unavailable';
  const refreshUsage = () => api('/ai/usage').then(setUsage).catch(() => {});
  function load() {
    setLoadError(''); api('/portfolio').then(setPortfolio).catch(e => setLoadError(e.message));
    api('/research').then(setResearch).catch(() => setResearch(null)); refreshUsage();
  }
  useEffect(load, []);
  useEffect(() => { if (messagesPane.current) messagesPane.current.scrollTop = messagesPane.current.scrollHeight; }, [messages, chatBusy, canRerun, pendingQuestion]);
  useEffect(() => { stageHeading.current?.focus({ preventScroll: true }); }, [stage]);
  function navigate(next) {
    if ((next === 'scenarios' || next === 'evidence') && !comparison && !calculating) { runSimulation(conditions); return; }
    setStage(next === 'evidence' ? 'scenarios' : next);
    window.history.replaceState(null, '', '#' + next); window.scrollTo({ top: 0, behavior: 'instant' });
    if (next === 'evidence') setTimeout(() => document.getElementById('evidence')?.scrollIntoView({ behavior: 'smooth' }), 100);
  }
  function resetConversation(next = conditions) {
    const id = conversationId.current; conversationId.current = null; requestVersion.current++;
    setMessages([]); setMessage(''); setPendingQuestion(null); setNeedsClarification(false); setDraft(next);
    if (id) api('/conversations/' + id, undefined, undefined, 'DELETE').catch(() => {});
  }
  async function explain(next = conditions) {
    const version = ++explanationVersion.current; setExplainBusy(true); setExplanation(null);
    try { const result = await api('/simulation/explain', { conditions: next }); if (version === explanationVersion.current) setExplanation(result); }
    catch (e) { if (version === explanationVersion.current) setExplanation({ mode: 'unavailable', message: e.message, scenarios: [] }); }
    finally { if (version === explanationVersion.current) setExplainBusy(false); refreshUsage(); }
  }
  async function runSimulation(next = draft) {
    if (calculating || chatBusy) return;
    const version = ++simulationVersion.current; ++explanationVersion.current;
    setCalculating(true); setCalculationError(''); setExplanation(null); setExplainBusy(false);
    setStage('scenarios'); window.history.replaceState(null, '', '#scenarios'); window.scrollTo({ top: 0, behavior: 'instant' });
    try {
      const result = await api('/simulation', { conditions: next });
      if (version !== simulationVersion.current) return;
      setPrevious(comparison); setComparison(result); setConditions(result.conditions); setDraft(result.conditions);
      setPendingQuestion(null); setNeedsClarification(false); setManualOpen(false);
      if (messages.length) setMessages(prev => [...prev, { role: 'assistant', mode: 'confirmation', text: '이 조건으로 다시 계산했어요. 결과를 보고 더 바꾸고 싶은 점을 말씀해 주세요.' }]);
      explain(result.conditions);
    } catch (e) { if (version === simulationVersion.current) setCalculationError(e.message); }
    finally { if (version === simulationVersion.current) setCalculating(false); }
  }
  async function sendText(text) {
    text = text.trim(); if (!text || chatBusy) return;
    const version = ++requestVersion.current; setMessage(''); setChatBusy(true);
    setMessages(prev => [...prev.slice(-19), { role: 'user', text }]);
    try {
      const response = await api('/conditions/extract', { message: text, existing: draft, selectedSymbol: symbol, conversationId: conversationId.current });
      if (version !== requestVersion.current) return;
      conversationId.current = response.conversationId; setPendingQuestion(response.pendingQuestion || null);
      setNeedsClarification(response.questions.length > 0);
      setMessages(prev => [...prev, { role: 'assistant', mode: response.mode, text: [response.message, ...response.questions].join('\n') }]);
      if (response.canApply) setDraft(response.conditions);
    } catch (e) { if (version === requestVersion.current) setMessages(prev => [...prev, { role: 'assistant', mode: 'unavailable', text: e.message }]); }
    finally { if (version === requestVersion.current) setChatBusy(false); refreshUsage(); }
  }
  if (!portfolio) return <div className="loading-screen"><span className="brand-circle">W</span><h1>나의 해외주식 절세 비교</h1>{loadError ? <><p role="alert">자산을 불러오지 못했어요. 백엔드 실행 상태를 확인해 주세요.</p><small>{loadError}</small><button className="button primary" onClick={load}>다시 연결</button></> : <p>시연 자산을 불러오고 있어요…</p>}</div>;
  const modeLabel = { gemini: 'AI 도우미', confirmation: '조건 확인', clarification: '함께 정해요', guidance: '이용 안내', unavailable: '연결 안내' };
  return <div className={'flow-app flow-stage-' + stage}>
    <ServiceHeader active={stage} onNavigate={navigate} canCompare={Boolean(comparison)}/>
    <main className="flow-main" id="main-content"><JourneyNav active={stage} onNavigate={navigate} canCompare={Boolean(comparison)}/>
      {stage === 'assets' && <AssetOverview portfolio={portfolio} onSimulate={() => runSimulation(conditions)} busy={calculating}/>}
      {stage === 'scenarios' && <div className="results-stage" id="scenarios">
        <div className="flow-page-heading"><div><span className="eyebrow">MY TAX SIMULATION</span><h1 ref={stageHeading} tabIndex={-1}>{previous ? '내 투자 의사를 반영한 시뮬레이션' : '내 자산의 절세 시나리오'}</h1><p>전체 보유 종목의 절세 효과와 계속 보유할 기회를 함께 비교하세요.</p></div><button className="button light" onClick={() => navigate('assets')}>내 자산 보기</button></div>
        <div className="result-condition-bar"><strong>반영한 조건</strong><ConditionSummary conditions={conditions}/><button disabled={calculating} onClick={() => navigate('conditions')}>대화로 바꾸기</button></div>
        {calculating ? <div className="calculating-result" role="status">모든 보유 종목의 손익과 매도 한도로 세 가지 시나리오를 계산하고 있어요…</div>
          : calculationError ? <div className="error-box" role="alert">{calculationError}<button className="button light" onClick={() => runSimulation(draft)}>다시 계산</button></div>
          : comparison && <PortfolioResults comparison={comparison} previous={previous} research={research} explanation={explanation} explainBusy={explainBusy} onExplain={() => explain()} onChat={() => navigate('conditions')} conditionSummary={<ConditionSummary conditions={conditions}/>}/>}
      </div>}
      {stage === 'conditions' && <section className="conversation-stage" id="conditions">
        <div className="flow-page-heading"><div><span className="eyebrow">LET'S MAKE IT YOURS</span><h1 ref={stageHeading} tabIndex={-1}>내 투자 생각을 들려주세요.</h1><p>팔고 싶지 않은 종목, 지키고 싶은 보유 비중을 편하게 이야기해 주세요.</p></div><button className="button ghost" onClick={() => navigate(comparison ? 'scenarios' : 'assets')}>{comparison ? '시뮬레이션으로 돌아가기' : '내 자산 보기'}</button></div>
        <div className="conversation-workspace">
          <section className="conversation-card" aria-label="투자 의사 대화">
            <header className="conversation-header"><span className="large-chat-orb"><Icon name="spark" size={25}/></span><div><h2>내 투자 의사 이야기하기</h2><p>현재 결과와 보유 종목을 함께 보고 있어요</p></div><button className="new-chat-button" disabled={chatBusy} title="적용된 조건은 유지하고 대화 초안을 초기화" onClick={() => resetConversation()}>새 대화</button></header>
            <div className="flow-chat-history" ref={messagesPane} aria-live="polite" aria-busy={chatBusy}>
              <div className="flow-chat-message assistant"><span className="flow-chat-avatar">W</span><div><small>AI 도우미</small><p>{comparison ? '결과에 팔고 싶지 않은 주식이 포함됐나요? 원하는 투자 방향을 알려주세요. 조건을 함께 정한 뒤 다시 시뮬레이션해 볼게요.' : '내가 지키고 싶은 투자 원칙을 알려주세요. 보유 종목과 매도 범위를 함께 정해 볼게요.'}</p></div></div>
              {!messages.length && <div className="conversation-suggestions"><p>이렇게 이야기해 보세요</p><button disabled={chatBusy} onClick={() => sendText('테슬라는 장기투자 중이라 팔 생각 없어.')}>테슬라는 장기투자라 안 팔래요</button><button disabled={chatBusy} onClick={() => sendText('조금 손해보더라도 공격적인 투자를 하고 싶어.')}>손해를 감수해도 공격적으로 투자할래요</button><button disabled={chatBusy} onClick={() => sendText('애플은 절반까지만 팔 수 있어.')}>애플은 절반까지만 팔 수 있어요</button></div>}
              {messages.map((m, i) => <div key={i} className={'flow-chat-message ' + m.role}>{m.role === 'assistant' && <span className="flow-chat-avatar">W</span>}<div>{m.role === 'assistant' && <small>{modeLabel[m.mode] || 'AI 도우미'}</small>}<p>{m.text}</p></div></div>)}
              {chatBusy && <div className="chat-working" role="status"><span/><span/><span/>말씀하신 투자 의사를 살펴보고 있어요</div>}
              {pendingQuestion && <div className="question-actions"><button disabled={chatBusy} onClick={() => sendText('네')}>네, 그렇게 할게요</button><button disabled={chatBusy} onClick={() => sendText('아니요')}>아니요</button></div>}
              {canRerun && <div className="in-chat-proposal simulation-reply"><div><strong>{hasDraft ? '이 조건으로 다시 시뮬레이션할까요?' : '현재 조건으로 다시 비교해 볼까요?'}</strong><ConditionSummary conditions={draft}/><p>더 이야기해도 좋아요. 아래 버튼을 누를 때 계산에 반영합니다.</p></div><button className="button primary" onClick={() => runSimulation(draft)}>이 조건으로 다시 시뮬레이션 <Icon name="arrow" size={16}/></button></div>}
            </div>
            <div className="flow-chat-composer"><form onSubmit={e => { e.preventDefault(); sendText(message); }}><textarea aria-label="투자 의사 입력" rows={2} maxLength={1000} value={message} disabled={chatBusy} placeholder="예: 테슬라는 안 팔고, 애플은 절반까지만 팔래" onChange={e => setMessage(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); sendText(message); } }}/><button type="submit" aria-label="메시지 전송" disabled={chatBusy || !message.trim()}><Icon name="send" size={21}/></button></form><p>Enter 전송 · Shift+Enter 줄바꿈 · 대화 중에는 기존 시뮬레이션이 바뀌지 않아요.</p></div>
          </section>
          <aside className="conversation-context">
            <section className="panel conversation-review"><span className="eyebrow">함께 정리하는 조건</span><h2>{hasDraft ? '다음 계산에 반영할 내용' : '현재 반영된 내용'}</h2><ConditionSummary conditions={draft}/><p className="context-hint">{needsClarification ? '위 질문에 답하면 계산 가능한 조건을 정리해 드려요.' : '대화로 조건을 더한 뒤 다시 시뮬레이션할 수 있어요.'}</p>{canRerun && <button className="button primary wide" onClick={() => runSimulation(draft)}>다시 시뮬레이션</button>}{hasDraft && <button className="button ghost wide" disabled={chatBusy} onClick={() => resetConversation()}>대화 초안 취소</button>}</section>
            <section className="panel selected-context"><span className="eyebrow">“이 종목”이라고 말하면</span><div className="context-stock"><StockMark symbol={symbol}/><div><strong>{names[symbol]}</strong><span>합산 {quantity}주</span></div></div><label className="stock-change-label">대화 기준 종목<select aria-label="대화 기준 종목" value={symbol} disabled={chatBusy} onChange={e => { resetConversation(draft); setSymbol(e.target.value); }}>{Object.entries(names).map(([s, name]) => <option value={s} key={s}>{name} ({s})</option>)}</select></label><p className="context-hint">다른 종목은 이름으로 말해 주세요. 시뮬레이션은 모든 보유 종목을 함께 계산합니다.</p></section>
            <details className="panel direct-editor" open={manualOpen} onToggle={e => setManualOpen(e.currentTarget.open)}><summary>직접 조건 수정 <span>+</span></summary><ManualForm conditions={draft} positions={portfolio.positions} disabled={chatBusy} onApply={next => { resetConversation(next); runSimulation(next); }}/></details>
          </aside>
        </div>
      </section>}
      <div className="demo-banner flow-demo-note"><Icon name="info" size={17}/><span>가상 계좌·가격·환율을 사용하는 시연입니다. 실제 기업 자료의 발표일과 시연 가격 기준은 다를 수 있으며, 주문·세금 신고는 실행하지 않습니다.</span></div>
      <details className="panel assumptions"><summary>계산 가정과 지원 범위</summary><ul>{portfolio.rule.assumptions.map(a => <li key={a}>{a}</li>)}<li>{portfolio.rule.rounding}</li><li>{portfolio.rule.settlementAssumption}</li></ul></details>
      <details className="flow-usage"><summary>AI 사용 상태</summary><p>{usage?.configured ? 'Gemini 키 설정됨' : '키 미설정'} · {usage?.model} · 누적 {usage?.calls ?? 0}/{usage?.maxCalls ?? 40}회 · 실제 청구액과 별도인 단가 환산 추정 ${Number(usage?.estimatedCostUsd ?? 0).toFixed(6)}</p></details>
      <footer className="service-footer"><div><span className="footer-brand">우리투자증권 <small>절세 시나리오</small></span><p>우리 WON 청년 IT 아카데미 · 해커톤 프로토타입</p></div><button className="top-link" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}>↑<span>TOP</span></button></footer>
    </main>
  </div>;
}
