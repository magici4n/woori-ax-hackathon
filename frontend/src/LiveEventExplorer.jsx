import React, { useState } from 'react';
import { HeroArtwork } from './Presentation.jsx';
import { Icon, StockMark, names, won } from './ui.jsx';

const kindLabel = { company: '기업 발표', filing: '공시', news: '기사', other: '검색 자료' };

function ArticleCard({ article }) {
  const analyzed = Boolean(article.summary);
  return <article className="live-article-card">
    <div className="live-article-heading"><span className="tag">{kindLabel[article.kind] || kindLabel.other}</span><span>{article.publishedAt ? `발행 ${article.publishedAt}` : '발행일 미확인'}</span></div>
    <h3><a href={article.url} target="_blank" rel="noreferrer">{article.title} ↗</a></h3>
    {article.eventDate && <p className="live-event-date">관련 일정 {article.eventDate} · {article.eventStatus === 'confirmed' ? '출처에 명시' : article.eventStatus === 'estimated' ? '추정' : '확인 필요'}</p>}
    {analyzed ? <div className="live-article-facts"><div><strong>자료 요약</strong><p>{article.summary}</p></div>
      <div><strong>성장 참고 요인</strong><p>{article.growth || '출처에서 확인되지 않았습니다.'}</p></div>
      <div><strong>함께 볼 위험</strong><p>{article.risk || '출처에서 확인되지 않았습니다.'}</p></div>
      <div><strong>AI 분석 코멘트</strong><p>{article.relevance || '이 자료만으로 보유·매도 판단을 내릴 근거가 충분하지 않습니다.'}</p></div>
    </div> : <p className="event-caveat">원문 링크는 확인했지만 분석에 필요한 사실을 충분히 검증하지 못했습니다.</p>}
  </article>;
}

function StockAnalysisCard({ analysis, sources, researchedAt }) {
  const citedSources = sources.filter(source => analysis.sourceIds.includes(source.id));
  return <div className="event-ai-summary"><div className="event-ai-title"><span className="analysis-spark"><Icon name="spark" size={29}/></span><div><strong>AI 분석 요약</strong><small>조사일 {researchedAt} · 근거 자료 {citedSources.length}건</small></div></div>
    <p className="event-ai-overview">{analysis.summary}</p>
    {citedSources.length === 1 && <p className="event-caveat">현재 확인된 자료 1건을 바탕으로 한 분석입니다.</p>}
    <div className="event-ai-points"><div><strong>성장 참고 요인</strong><p>{analysis.growth || '현재 자료에서 확인되지 않았습니다.'}</p></div>
      <div><strong>함께 볼 위험</strong><p>{analysis.risk || '현재 자료에서 확인되지 않았습니다.'}</p></div>
      <div><strong>보유·매도 판단에 미치는 영향</strong><p>{analysis.decisionPoint || '현재 자료만으로는 판단하기 어렵습니다.'}</p></div>
      {analysis.nextCheck && <div><strong>다음에 확인할 내용</strong><p>{analysis.nextCheck}</p></div>}
    </div>
    {analysis.keywords?.length > 0 && <div className="event-ai-keywords" aria-label="주요 키워드">{analysis.keywords.map(keyword => <span key={keyword}>#{keyword}</span>)}</div>}
    <div className="event-ai-sources"><strong>분석 근거</strong>{citedSources.map(source => <a key={source.id} href={source.url} target="_blank" rel="noreferrer">{source.title} ↗</a>)}</div>
  </div>;
}

export function EventExplorer({ portfolio, liveResearch, researchBusy, researchError, onResearch, selectedSymbol, onSelectSymbol, onChat }) {
  const [tab, setTab] = useState('summary');
  const [query, setQuery] = useState('');
  const [kind, setKind] = useState('all');
  const symbols = [...new Set(portfolio.positions.map(position => position.lot.symbol))];
  const articles = [...(liveResearch?.articles || [])].sort((a, b) => (b.publishedAt || '').localeCompare(a.publishedAt || ''));
  const sources = liveResearch?.research?.sources || [];
  const selectedArticles = articles.filter(article => article.symbol === selectedSymbol && (kind === 'all' || article.kind === kind));
  const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Seoul' });
  const stockAnalysis = liveResearch?.stockAnalyses?.find(analysis => analysis.symbol === selectedSymbol);
  const holding = portfolio.positions.filter(position => position.lot.symbol === selectedSymbol);
  const quantity = holding.reduce((sum, position) => sum + Number(position.lot.quantity), 0);
  const value = holding.reduce((sum, position) => sum + Number(position.marketValueKrw), 0);
  const unrealized = holding.reduce((sum, position) => sum + Number(position.unrealizedKrw), 0);
  const visibleSymbols = symbols.filter(symbol => {
    const matches = articles.some(article => article.symbol === symbol && (kind === 'all' || article.kind === kind));
    return (kind === 'all' || matches) && `${names[symbol] || ''} ${symbol}`.toLowerCase().includes(query.trim().toLowerCase());
  });
  return <div className="events-page">
    <section className="reference-hero compact"><div className="reference-hero-copy"><span className="reference-kicker">이벤트 분석</span><h1>매도 검토를 위해<br/><em>주요 기업 이벤트를 먼저 확인해요.</em></h1><p>보유 종목의 실제 기사·공시를 AI가 정리했습니다. 실적 일정과 성장 요인, 함께 볼 위험을 살펴보세요.</p></div></section>
    <section className="reference-main-card live-research-panel"><div className="reference-section-head"><div><h2>최신 기업·실적 자료 조사</h2><p>한국 주요 기사와 기업 발표·공시를 바탕으로 투자에 필요한 정보를 확인하세요.</p></div><button className="button light" disabled={researchBusy} onClick={onResearch}>{researchBusy ? '기사 검색·AI 분석 중…' : liveResearch ? '기사 다시 검색·분석' : '최신 자료 조사 시작'}</button></div>
      {researchError && <p role="alert" className="error-text">{researchError}</p>}
      {liveResearch?.warnings?.length > 0 && <div role="alert" className="error-text"><strong>완료하지 못한 단계</strong><ul>{liveResearch.warnings.map((warning, index) => <li key={index}>{warning}</li>)}</ul></div>}
      {liveResearch ? <><p className="muted">조사 날짜 {liveResearch.researchedAt} · 출처 {sources.length}개 · 종목 연결 자료 {articles.length}건. {liveResearch.message}</p>
        <details className="live-all-sources"><summary>검색한 원문 출처 전체 보기</summary><div>{sources.map(source => <a key={source.id} href={source.url} target="_blank" rel="noreferrer">{source.title} ↗</a>)}</div></details></>
        : <p className="event-caveat">아직 검색하지 않았습니다. 조사 시작 후 종목별 기사·공시와 요약이 여기에 표시됩니다.</p>}
    </section>
    <div className="event-workspace"><section className="reference-main-card event-list"><div className="reference-section-head"><div><h2>보유 종목별 기업 자료</h2><p>종목을 선택하면 AI가 요약한 자료와 원문을 확인할 수 있어요.</p></div></div>
      <label className="event-search-field"><span aria-hidden="true">⌕</span><input className="event-search" value={query} onChange={event => setQuery(event.target.value)} aria-label="보유 종목 검색" placeholder="종목명 또는 종목코드 검색"/></label>
      <div className="event-filters" aria-label="자료 종류">{[['all', '전체'], ['company', '기업 발표'], ['filing', '공시'], ['news', '뉴스/리포트']].map(([filter, label]) => <button key={filter} className={kind === filter ? 'active' : ''} onClick={() => setKind(filter)}>{label}</button>)}</div>
      <div className="event-list-heading"><span>종목명</span><span>확인된 자료</span><span>다음 일정</span></div>
      <div className="event-rows">{visibleSymbols.map(symbol => { const stockArticles = articles.filter(article => article.symbol === symbol); const nextDate = stockArticles.map(article => article.eventDate).filter(value => value && value >= today).sort()[0]; const kinds = [...new Set(stockArticles.map(article => article.kind))]; const analyzed = liveResearch?.stockAnalyses?.some(analysis => analysis.symbol === symbol); return <button key={symbol} className={'event-row ' + (selectedSymbol === symbol ? 'active' : '')} onClick={() => { onSelectSymbol(symbol); setTab('summary'); }}><StockMark symbol={symbol}/><span className="event-row-name"><strong>{names[symbol] || symbol}</strong><small>{symbol}</small></span><span className="event-row-kinds">{kinds.length ? kinds.slice(0, 2).map(item => <small key={item} className={'event-kind ' + item}>{kindLabel[item] || '검색 자료'}</small>) : <small>자료 없음</small>}</span><span className="event-row-meta">{nextDate || '확인된 일정 없음'}<small>{stockArticles.length}건 · {analyzed ? '종합 분석 완료' : '분석 미완료'}</small></span><b aria-hidden="true">›</b></button>; })}{visibleSymbols.length === 0 && <p className="event-caveat">조건에 맞는 자료가 없습니다.</p>}</div>
      <p className="event-caveat">자료 내용과 실적 예정일은 연결된 원문에서 다시 확인하세요.</p>
    </section><section className="reference-main-card event-detail"><div className="event-detail-top"><div className="stock-cell"><StockMark symbol={selectedSymbol}/><div><h2>{names[selectedSymbol] || selectedSymbol}</h2><small>{selectedSymbol}</small></div></div><span className="tag">보유 {quantity}주 · {won(value)}</span></div>
      <div className="event-holding-stats"><div><span>평가금액</span><strong>{won(value)}</strong></div><div><span>평가손익</span><strong>{won(unrealized)}</strong></div><div><span>확인한 자료</span><strong>{selectedArticles.length}건</strong></div></div>
      <div className="event-tabs" role="tablist" aria-label="기업 자료 보기">{[['summary', 'AI 분석 요약'], ['materials', '기업 자료'], ['decision', '매도 검토 포인트']].map(([id, label]) => <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? 'active' : ''} onClick={() => setTab(id)}>{label}</button>)}</div>
      {tab === 'summary' && (stockAnalysis ? <StockAnalysisCard analysis={stockAnalysis} sources={sources} researchedAt={liveResearch.researchedAt}/> : selectedArticles.length ? <div className="event-ai-summary"><strong>종목 종합 분석 미완료</strong><p>기사 링크는 확인했지만 종목 전체를 해석할 근거가 충분하지 않거나 AI 분석이 완료되지 않았습니다. 기업 자료 탭에서 자료와 원문을 확인해 주세요.</p></div> : <p className="event-caveat">이 종목에 연결된 자료가 없습니다. 전체 출처를 확인해 주세요.</p>)}
      {tab === 'materials' && (selectedArticles.length ? <div className="live-article-list">{selectedArticles.map(article => <ArticleCard key={article.sourceId} article={article}/>)}</div> : <p className="event-caveat">이 종목에 연결된 기업 자료가 없습니다.</p>)}
      {tab === 'decision' && (selectedArticles.length ? <div className="event-decision-list">{selectedArticles.map(article => <div key={article.sourceId}><strong>{article.title}</strong><p>{article.relevance || '보유·매도 판단에 필요한 근거를 확인하지 못했습니다.'}</p><p>성장: {article.growth || '확인 불가'} · 위험: {article.risk || '확인 불가'}</p><a href={article.url} target="_blank" rel="noreferrer">원문 확인 ↗</a></div>)}</div> : <p className="event-caveat">이 종목에 연결된 분석 자료가 없습니다.</p>)}
    </section></div>
    <section className="event-chat-launch"><div><span className="reference-kicker">WON AI 맞춤 절세 상담</span><h2>어떤 주식을 남기고,<br/><em>어떤 주식을 매도할지 고민되시나요?</em></h2><p>기업 자료를 살펴본 뒤, 보유 의사와 매도 범위를 대화로 정리해 보세요.</p><button className="button primary" disabled={researchBusy} onClick={onChat}>{researchBusy ? '최신 자료 조사 중…' : 'AI와 절세 계획 세우기'} <Icon name="arrow" size={18}/></button></div><div className="event-chat-visual"><HeroArtwork variant="chat" label="맞춤 절세 상담"/><div className="event-chat-note"><span className="large-chat-orb"><Icon name="spark" size={23}/></span><p>어떤 종목을 오래 보유하고 싶으신가요?<br/>매도하고 싶은 범위도 함께 알려주세요.</p></div></div></section>
  </div>;
}
