import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.API_URL || 'http://localhost:8080';

export const options = {
  vus: 10,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
    checks: ['rate>0.95'],
  },
};

export default function () {
  const response = http.get(`${BASE_URL}/flights?origin=SVO&destination=LED`);

  check(response, {
    'GET /flights returns 200': (r) => r.status === 200,
    'response is not empty': (r) => r.body && r.body.length > 2,
  });

  sleep(1);
}
