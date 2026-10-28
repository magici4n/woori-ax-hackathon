const RATE_URL = 'https://api.frankfurter.dev/v2/rate/USD/KRW';
const CACHE_MS = 60 * 60 * 1000;
let cachedQuote;
let pendingQuote;

export function parseExchangeRate(data) {
  if (data?.base !== 'USD' || data?.quote !== 'KRW' || !Number.isFinite(data.rate)
      || data.rate <= 0 || !/^\d{4}-\d{2}-\d{2}$/.test(data.date || '')) {
    throw new Error('환율 정보를 확인하지 못했습니다.');
  }
  return { rate: data.rate, date: data.date };
}

export function loadExchangeRate() {
  if (cachedQuote && Date.now() - cachedQuote.loadedAt < CACHE_MS) return Promise.resolve(cachedQuote);
  if (pendingQuote) return pendingQuote;
  pendingQuote = (async () => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
      const response = await fetch(RATE_URL, { signal: controller.signal });
      if (!response.ok) throw new Error('환율을 불러오지 못했습니다.');
      cachedQuote = { ...parseExchangeRate(await response.json()), loadedAt: Date.now() };
      return cachedQuote;
    } finally {
      clearTimeout(timeout);
      pendingQuote = null;
    }
  })();
  return pendingQuote;
}

// This conversion is for display only; the tax calculation keeps its KRW inputs.
export function portfolioMoney(value, currency, quote, signed = false) {
  const amount = Number(value ?? 0);
  const prefix = signed && amount > 0 ? '+' : '';
  if (currency === 'KRW') {
    return prefix + new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 }).format(amount) + '원';
  }
  if (!quote?.rate) return '—';
  return prefix + new Intl.NumberFormat('en-US', {
    style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 2,
  }).format(amount / quote.rate);
}
