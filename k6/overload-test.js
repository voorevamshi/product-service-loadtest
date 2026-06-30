/**
 * k6 Load Test — Product Service Overload Simulation
 * =====================================================
 * Stages:
 *   0-30s  : Warm-up at 50 QPS
 *   30-60s : Normal load at 150 QPS (t3.micro safe zone)
 *   60-90s : Overload at 200 QPS  ← Watch errors spike here
 *   90-120s: Beyond limit 300 QPS ← OOM / timeout territory
 *   120-150s: Recovery back to 50 QPS
 *
 * Run:
 *   k6 run k6/overload-test.js --out json=results.json
 *   k6 run k6/overload-test.js --out influxdb=http://localhost:8086/k6
 *
 * Install k6:  https://k6.io/docs/get-started/installation/
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ── Custom metrics ────────────────────────────────────────────────────────────
const rateLimitErrors  = new Counter('rate_limit_429');
const serverErrors     = new Counter('server_errors_5xx');
const timeoutErrors    = new Counter('timeout_errors');
const requestDuration  = new Trend('product_api_duration');
const errorRate        = new Rate('error_rate');

// ── Test configuration ────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    overload_test: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 500,
      stages: [
        { target: 50,  duration: '30s' },  // Warm-up
        { target: 150, duration: '30s' },  // Safe zone — expect 0 errors
        { target: 200, duration: '30s' },  // Over capacity — 429s start appearing
        { target: 300, duration: '30s' },  // Severe overload — OOM / 503 / timeouts
        { target: 50,  duration: '30s' },  // Recovery
      ],
    },
  },

  thresholds: {
    // These will FAIL at 200+ QPS, which is intentional — shows the boundary
    'http_req_duration': ['p(95)<2000'],   // 95% under 2s
    'http_req_duration': ['p(99)<5000'],   // 99% under 5s
    'error_rate':        ['rate<0.05'],    // Under 5% error rate
  },
};

const BASE_URL = 'http://localhost:8080';
//const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Seed product IDs for GET requests (created by DataSeeder)
const PRODUCT_IDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

export default function () {
  // Mix of read (80%) and write (20%) to simulate real traffic
  const rand = Math.random();

  if (rand < 0.5) {
    testGetById();
  } else if (rand < 0.8) {
    testListProducts();
  } else if (rand < 0.9) {
    testGetBySku();
  } else {
    testCreate();
  }
}

// ── Individual scenario functions ─────────────────────────────────────────────

function testGetById() {
  const id = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];
  const res = http.get(`${BASE_URL}/api/v1/products/${id}`, {
    timeout: '5s',
    tags: { endpoint: 'GET /products/:id' },
  });

  trackResponse(res);

  check(res, {
    'GET by ID: status 200 or 429': (r) =>
      r.status === 200 || r.status === 429,
  });
}

function testListProducts() {
  const res = http.get(`${BASE_URL}/api/v1/products?page=0&size=20`, {
    timeout: '5s',
    tags: { endpoint: 'GET /products' },
  });

  trackResponse(res);

  check(res, {
    'LIST: status 200 or 429': (r) =>
      r.status === 200 || r.status === 429,
  });
}

function testGetBySku() {
  const skus = ['LP-001', 'WM-002', 'MK-003', 'RS-005', 'OC-006'];
  const sku  = skus[Math.floor(Math.random() * skus.length)];
  const res  = http.get(`${BASE_URL}/api/v1/products/sku/${sku}`, {
    timeout: '5s',
    tags: { endpoint: 'GET /products/sku/:sku' },
  });

  trackResponse(res);
}

function testCreate() {
  const payload = JSON.stringify({
    name: `Load Test Product ${Date.now()}`,
    description: 'Created during load test',
    price: (Math.random() * 500 + 10).toFixed(2),
    sku: `LT-${Date.now()}-${Math.floor(Math.random() * 9999)}`,
    category: 'LoadTest',
    stockQuantity: 100,
  });

  const res = http.post(`${BASE_URL}/api/v1/products`, payload, {
    headers: { 'Content-Type': 'application/json' },
    timeout: '5s',
    tags: { endpoint: 'POST /products' },
  });

  trackResponse(res);
}

// ── Metrics helper ────────────────────────────────────────────────────────────
function trackResponse(res) {
  requestDuration.add(res.timings.duration);

  if (res.status === 429) {
    rateLimitErrors.add(1);
    errorRate.add(1);
    console.log(`[429] Rate limited — QPS exceeded t3.micro capacity`);
  } else if (res.status === 503) {
    serverErrors.add(1);
    errorRate.add(1);
    console.log(`[503] Service unavailable — Tomcat thread pool exhausted`);
  } else if (res.status >= 500) {
    serverErrors.add(1);
    errorRate.add(1);
    console.log(`[${res.status}] Server error — possible OOM or DB pool exhaustion`);
  } else if (res.error_code === 1050 || res.error_code === 1000) {
    timeoutErrors.add(1);
    errorRate.add(1);
    console.log(`[TIMEOUT] Request timed out after 5s — server overwhelmed`);
  } else {
    errorRate.add(0);
  }
}
