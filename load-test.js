import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8081';
const token = __ENV.TOKEN || '';
const sameSession = __ENV.SAME_SESSION === 'true';

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '5m',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<30000'],
  },
};

export default function () {
  const id = `${__VU}-${__ITER}`;
  const sessionId = sameSession ? 'load-lock-contention' : `load-session-${__VU}`;
  const payload = JSON.stringify({
    sessionId,
    clientMessageId: `load-message-${id}`,
    message: '你好，请介绍一下售后政策',
  });

  const response = http.post(`${baseUrl}/chat/ask`, payload, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      'X-Trace-Id': `load-${id}`,
    },
    timeout: '45s',
  });

  check(response, {
    'status is 200 or expected lock rejection': (r) => r.status === 200 || (sameSession && r.status >= 400 && r.status < 500),
  });
  if (__ENV.PRINT_TIMINGS === 'true' && response.status === 200) {
    try {
      const body = JSON.parse(response.body);
      const spans = (body.events || []).map((event) => ({
        node: event.node,
        traceId: event.state && event.state.traceId,
        spanId: event.state && event.state.spanId,
        elapsedMs: event.state && event.state.elapsedMs,
      }));
      console.log(JSON.stringify({ sessionId, spans }));
    } catch (e) {
      console.log(JSON.stringify({ sessionId, timingParseError: String(e) }));
    }
  }
  sleep(1);
}
