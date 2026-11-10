// Local stand-in for the API Gateway HTTP API (capability serverless-local-compose), Node 22 without dependencies.
// Each /api/* request becomes an HTTP API payload 2.0 event, is sent to the Lambda Runtime Interface Emulator of the
// "api" service, and the Lambda result goes back as an HTTP response (status, headers, cookies, body).
// Like the HTTP API: a function error answers 500 {"message":"Internal Server Error"} and an invocation over 30 s
// answers 503 {"message":"Service Unavailable"}. The emulator runs one invocation at a time, so requests are queued.
'use strict';

const http = require('node:http');
const crypto = require('node:crypto');

const PORT = Number(process.env.PORT || 3000);
const LAMBDA_URL = process.env.LAMBDA_URL || 'http://api:8080/2015-03-31/functions/function/invocations';
const TIMEOUT_MS = Number(process.env.TIMEOUT_MS || 30000);

let queue = Promise.resolve();

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

function toEvent(req, body) {
  const url = new URL(req.url, 'http://localhost');
  const headers = {};
  for (const [name, value] of Object.entries(req.headers)) {
    headers[name.toLowerCase()] = Array.isArray(value) ? value.join(',') : value;
  }
  const query = {};
  for (const [key, value] of url.searchParams) query[key] = key in query ? `${query[key]},${value}` : value;
  const cookies = headers.cookie ? headers.cookie.split(';').map((c) => c.trim()).filter(Boolean) : undefined;
  delete headers.cookie;
  const now = new Date();
  const text = body.length ? body.toString('utf8') : undefined;
  return {
    version: '2.0',
    routeKey: 'ANY /api/{proxy+}',
    rawPath: url.pathname,
    rawQueryString: url.search.replace(/^\?/, ''),
    cookies,
    headers,
    queryStringParameters: Object.keys(query).length ? query : undefined,
    pathParameters: { proxy: url.pathname.replace(/^\/api\/?/, '') },
    requestContext: {
      accountId: 'local',
      apiId: 'local',
      domainName: headers.host || 'localhost',
      http: {
        method: req.method,
        path: url.pathname,
        protocol: `HTTP/${req.httpVersion}`,
        sourceIp: req.socket.remoteAddress,
        userAgent: headers['user-agent'] || '',
      },
      requestId: crypto.randomUUID(),
      routeKey: 'ANY /api/{proxy+}',
      stage: '$default',
      time: now.toUTCString(),
      timeEpoch: now.getTime(),
    },
    body: text,
    isBase64Encoded: false,
  };
}

async function invoke(event) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const response = await fetch(LAMBDA_URL, { method: 'POST', body: JSON.stringify(event), signal: controller.signal });
    return { status: response.status, text: await response.text() };
  } finally {
    clearTimeout(timer);
  }
}

function send(res, status, body, headers = {}) {
  res.writeHead(status, { 'Content-Type': 'application/json', ...headers });
  res.end(typeof body === 'string' ? body : JSON.stringify(body));
}

const server = http.createServer(async (req, res) => {
  const started = Date.now();
  if (!req.url.startsWith('/api/') && req.url !== '/api') {
    send(res, 404, { message: 'Not Found' });
    return;
  }
  const body = await readBody(req);
  const event = toEvent(req, body);

  // one invocation at a time (Runtime Interface Emulator)
  const run = queue.then(() => invoke(event));
  queue = run.catch(() => undefined);

  let result;
  try {
    result = await run;
  } catch (e) {
    const timedOut = e && e.name === 'AbortError';
    console.log(`${req.method} ${req.url} -> ${timedOut ? 503 : 502} (${Date.now() - started} ms) ${timedOut ? 'timeout' : e.message}`);
    send(res, timedOut ? 503 : 502, { message: timedOut ? 'Service Unavailable' : 'Bad Gateway' });
    return;
  }

  let output;
  try {
    output = JSON.parse(result.text);
  } catch {
    output = null;
  }
  if (!output || typeof output !== 'object' || output.errorType || output.errorMessage || typeof output.statusCode !== 'number') {
    console.log(`${req.method} ${req.url} -> 500 (${Date.now() - started} ms) function error: ${result.text.slice(0, 300)}`);
    send(res, 500, { message: 'Internal Server Error' });
    return;
  }

  const headers = { ...(output.headers || {}) };
  if (Array.isArray(output.cookies) && output.cookies.length) headers['Set-Cookie'] = output.cookies;
  const payload = output.body == null ? '' : output.isBase64Encoded ? Buffer.from(output.body, 'base64') : output.body;
  res.writeHead(output.statusCode, headers);
  res.end(payload);
  console.log(`${req.method} ${req.url} -> ${output.statusCode} (${Date.now() - started} ms)`);
});

server.listen(PORT, () => console.log(`HTTP API gateway on :${PORT} -> ${LAMBDA_URL}`));
