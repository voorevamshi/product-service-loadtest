/**
 * k6 Spike Test — Sudden burst from 0 → 300 QPS
 * ================================================
 * This is the most brutal scenario for t3.micro:
 *  - No warm-up; all 300 req/s arrive simultaneously
 *  - JVM hasn't JIT-compiled hot paths yet
 *  - Connection pool hasn't stabilized
 *
 * Expected failures:
 *  - HTTP 503: Tomcat accept-queue full (overflow beyond 100 queued)
 *  - HTTP 429: Resilience4j rate limiter firing
 *  - java.lang.OutOfMemoryError if rate limiter is disabled
 *  - Connection reset / ECONNREFUSED if all 800 connections used
 *
 * Run: k6 run k6/spike-test.js
 */

import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('spike_error_rate');

export const options = {
  scenarios: {
    spike: {
      executor: 'constant-arrival-rate',
      rate: 300,           // Immediate 300 QPS — no ramp
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 300,
      maxVUs: 600,
    },
  },
  thresholds: {
    'spike_error_rate': ['rate<1.0'],   // Anything goes — we WANT to see failures
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/products/1`, { timeout: '5s' });

  const ok = check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
    'not a 5xx error':      (r) => r.status < 500,
  });

  errorRate.add(!ok);

  // Log every non-200 to understand which failure mode hits first
  if (res.status !== 200) {
    console.log(
      `status=${res.status} ` +
      `duration=${res.timings.duration.toFixed(0)}ms ` +
      `body=${res.body ? res.body.substring(0, 100) : 'empty'}`
    );
  }
}

// Summary printed after the test
export function handleSummary(data) {
  const metrics = data.metrics;
  return {
    stdout: `
╔══════════════════════════════════════════════════════════╗
║           SPIKE TEST SUMMARY — t3.micro @ 300 QPS       ║
╠══════════════════════════════════════════════════════════╣
║ Total requests : ${metrics.http_reqs?.values?.count ?? 'N/A'}
║ Error rate     : ${((metrics.spike_error_rate?.values?.rate ?? 0) * 100).toFixed(1)}%
║ p95 latency    : ${(metrics.http_req_duration?.values?.['p(95)'] ?? 0).toFixed(0)} ms
║ p99 latency    : ${(metrics.http_req_duration?.values?.['p(99)'] ?? 0).toFixed(0)} ms
║ Max latency    : ${(metrics.http_req_duration?.values?.max ?? 0).toFixed(0)} ms
╚══════════════════════════════════════════════════════════╝
Expected on t3.micro:
  • 429s fire immediately (Resilience4j protecting the JVM)
  • Without rate limiter: p99 > 30s, heap fills, GC thrashes
  • 503s if accept-count queue (100) is exceeded
`,
  };
}
