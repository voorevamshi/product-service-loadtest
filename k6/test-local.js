import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 10,           // 10 virtual users
  duration: '30s',   // Run for 30 seconds
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/products/2`);
  
  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  sleep(1); // Wait 1 second between iterations
}