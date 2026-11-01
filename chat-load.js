import http from 'k6/http';
import ws from 'k6/ws';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const SOCKET_URL = BASE.replace('http', 'ws') + '/ws';
const PEAK = Number(__ENV.VUS || 200);
const SEND_EVERY_MS = Number(__ENV.SEND_EVERY_MS || 15000);
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

const latency = new Trend('delivery_latency', true);
const sent = new Counter('messages_sent');
const received = new Counter('messages_received');

export const options = {
  scenarios: {
    chat: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: Math.ceil(PEAK / 2) },
        { duration: '1m', target: PEAK },
        { duration: '2m', target: PEAK },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '70s',
    },
  },
  thresholds: {
    delivery_latency: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

function authed(token) {
  return { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } };
}

function frame(command, headers, body = '') {
  const lines = Object.entries(headers).map(([key, value]) => `${key}:${value}`).join('\n');
  return `${command}\n${lines}\n\n${body}\0`;
}

export function setup() {
  const stamp = Date.now();
  const registered = http.post(
    `${BASE}/api/auth/register`,
    JSON.stringify({ email: `owner${stamp}@load.test`, displayName: 'Owner', password: 'password123' }),
    JSON_HEADERS,
  ).json();
  const auth = authed(registered.token);
  const workspace = http.post(`${BASE}/api/workspaces`, JSON.stringify({ name: 'Load test' }), auth).json();
  const channels = http.get(`${BASE}/api/workspaces/${workspace.id}/channels`, auth).json();
  return { stamp, inviteCode: workspace.inviteCode, channelId: channels[0].id };
}

let session = null;

function join(data) {
  const registered = http.post(
    `${BASE}/api/auth/register`,
    JSON.stringify({ email: `vu${__VU}-${data.stamp}@load.test`, displayName: `User ${__VU}`, password: 'password123' }),
    JSON_HEADERS,
  ).json();
  http.post(`${BASE}/api/workspaces/join`, JSON.stringify({ inviteCode: data.inviteCode }), authed(registered.token));
  return { token: registered.token, userId: registered.user.id };
}

export default function (data) {
  if (!session) session = join(data);

  const response = ws.connect(SOCKET_URL, {}, socket => {
    socket.on('open', () => {
      socket.send(frame('CONNECT', { 'accept-version': '1.2', Authorization: `Bearer ${session.token}` }));
    });

    socket.on('message', raw => {
      if (raw.startsWith('CONNECTED')) {
        socket.send(frame('SUBSCRIBE', { id: 'sub-0', destination: `/topic/channel.${data.channelId}` }));
        socket.setInterval(() => {
          const content = JSON.stringify({ content: `ping ${Date.now()}` });
          socket.send(frame('SEND', {
            destination: `/app/channels/${data.channelId}/messages`,
            'content-type': 'application/json',
          }, content));
          sent.add(1);
        }, SEND_EVERY_MS);
      } else if (raw.startsWith('MESSAGE')) {
        const body = raw.slice(raw.indexOf('\n\n') + 2).replace(/\0$/, '');
        const event = JSON.parse(body);
        if (event.type !== 'message.created') return;
        received.add(1);
        const match = /^ping (\d+)$/.exec(event.data.content);
        if (match && event.data.author.id === session.userId) {
          latency.add(Date.now() - Number(match[1]));
        }
      }
    });

    socket.setTimeout(() => socket.close(), 60000);
  });

  check(response, { 'websocket upgraded': r => r && r.status === 101 });
}