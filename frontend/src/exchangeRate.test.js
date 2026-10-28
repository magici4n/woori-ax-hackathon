import test from 'node:test';
import assert from 'node:assert/strict';
import { parseExchangeRate, portfolioMoney } from './exchangeRate.js';

test('USD amounts divide KRW by the quoted KRW per dollar, including signed gains', () => {
  const quote = { rate: 1250, date: '2026-10-28' };
  assert.equal(portfolioMoney(3450000, 'USD', quote), '$2,760.00');
  assert.equal(portfolioMoney(450000, 'USD', quote, true), '+$360.00');
  assert.equal(portfolioMoney(-450000, 'USD', quote, true), '-$360.00');
  assert.equal(portfolioMoney(0, 'USD', quote, true), '$0.00');
});

test('KRW display works without a rate; missing USD rates do not fabricate a value', () => {
  assert.equal(portfolioMoney(594000, 'KRW', null), '594,000원');
  assert.equal(portfolioMoney(450000, 'KRW', null, true), '+450,000원');
  assert.equal(portfolioMoney(3450000, 'USD', null), '—');
});

test('rejects wrong currency pairs, invalid rates, and missing dates', () => {
  const valid = { base: 'USD', quote: 'KRW', rate: 1325.78, date: '2026-10-28' };
  assert.deepEqual(parseExchangeRate(valid), { rate: 1325.78, date: '2026-10-28' });
  for (const data of [null, { ...valid, base: 'KRW' }, { ...valid, rate: 0 },
    { ...valid, rate: Infinity }, { ...valid, date: '' }]) {
    assert.throws(() => parseExchangeRate(data));
  }
});

test('shares concurrent requests, caches success, and allows retry after failure', async t => {
  const { loadExchangeRate } = await import('./exchangeRate.js?request-test');
  let calls = 0;
  t.mock.method(globalThis, 'fetch', async url => {
    assert.equal(url, 'https://api.frankfurter.dev/v2/rate/USD/KRW');
    calls++;
    if (calls === 1) throw new Error('network unavailable');
    return { ok: true, json: async () => ({ base: 'USD', quote: 'KRW', rate: 1250, date: '2026-10-28' }) };
  });
  await assert.rejects(loadExchangeRate());
  const first = loadExchangeRate();
  const second = loadExchangeRate();
  assert.equal(first, second);
  assert.equal((await first).rate, 1250);
  assert.equal((await loadExchangeRate()).rate, 1250);
  assert.equal(calls, 2);
});
