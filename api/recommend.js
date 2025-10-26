import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = 'http://localhost:8080';
const USER_ID = 'f7bbe752-b4a2-4311-b1e2-0f04e80e4ef2';
const SEED = 'spotify:track:0cYohCh24y1aMjJmcS9RBl';

export const options = {
  scenarios: {
    rps10: {
      executor: 'constant-arrival-rate',
      duration: '2m',
      rate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 10,
    },
  },
};

export default function () {
  const res = http.get(`${BASE}/recommend?userId=${USER_ID}&seed=${encodeURIComponent(SEED)}&limit=20`);
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
  sleep(0.1);
}
