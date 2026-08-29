import http from 'k6/http';
import encoding from 'k6/encoding';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = stringEnv(
  'BASE_URL',
  'http://localhost:8084/api/order-service',
).replace(/\/+$/, '');
const AUTH_TOKEN = stringEnv('AUTH_TOKEN', '').replace(/^Bearer\s+/i, '');
const TENANT_ID = stringEnv(
  'TENANT_ID',
  '7bbdc9de_a2b8_4cf2_9f31_dbd636121269',
);
const HTTP_TIMEOUT = stringEnv('HTTP_TIMEOUT', '30s');

const ENDPOINT_CATALOG = Object.freeze({
  pessimistic: Object.freeze({
    name: 'pessimistic-lock',
    path: '/test/order/pessimisticLock',
    productOptionId: integerEnv('PESSIMISTIC_PRODUCT_OPTION_ID', 10, 1),
    quantity: integerEnv('PESSIMISTIC_QUANTITY', 1, 1),
    requestUpdateVersion: integerEnv('PESSIMISTIC_VERSION', 1, 1),
    booleanResponse: true,
  }),
  conditional: Object.freeze({
    name: 'conditional-update',
    path: '/test/order/conditionalUpdate',
    productOptionId: integerEnv('CONDITIONAL_PRODUCT_OPTION_ID', 20, 1),
    quantity: integerEnv('CONDITIONAL_QUANTITY', 1, 1),
    requestUpdateVersion: integerEnv('CONDITIONAL_VERSION', 1, 1),
    booleanResponse: true,
  }),
  purchase: Object.freeze({
    name: 'purchase-product',
    path: '/order/purchaseProduct',
    productOptionId: integerEnv('PURCHASE_PRODUCT_OPTION_ID', 39, 1),
    quantity: integerEnv('PURCHASE_QUANTITY', 1, 1),
    requestUpdateVersion: integerEnv('PURCHASE_VERSION', 1, 1),
    booleanResponse: false,
  }),
});

const SELECTED_ENDPOINTS = selectEndpoints(stringEnv('ENDPOINTS', 'all'));
const REQUEST_HEADERS = Object.freeze({
  Authorization: `Bearer ${AUTH_TOKEN}`,
  'Content-Type': 'application/json',
  'X-Tenant-Id': TENANT_ID,
});

const businessFailures = new Rate('business_failures');
const businessSuccesses = new Counter('business_successes');

const SAFE_TPS = integerEnv('SAFE_TPS', 500, 1);
const SPIKE_TPS = integerEnv('SPIKE_TPS', 5000, 1);
const P95_MS = integerEnv('P95_MS', 500, 1);
const MAX_ERROR_RATE = numberEnv('MAX_ERROR_RATE', 0.01, 0, 1);

const DEFAULT_PRE_ALLOCATED_VUS = Math.max(
  100,
  Math.ceil((SAFE_TPS * P95_MS * 1.2) / 1000),
);
const PRE_ALLOCATED_VUS = integerEnv(
  'PRE_ALLOCATED_VUS',
  DEFAULT_PRE_ALLOCATED_VUS,
  1,
);
const SPIKE_PRE_ALLOCATED_VUS = integerEnv(
  'SPIKE_PRE_ALLOCATED_VUS',
  Math.max(100, Math.ceil((SPIKE_TPS * P95_MS * 1.2) / 1000)),
  1,
);
const CONFIGURED_MAX_VUS = integerEnv('MAX_VUS', 10000, 1);

export const config = Object.freeze({
  baseUrl: BASE_URL,
  safeTps: SAFE_TPS,
  spikeTps: SPIKE_TPS,
  p95Ms: P95_MS,
  maxErrorRate: MAX_ERROR_RATE,
  preAllocatedVUs: PRE_ALLOCATED_VUS,
  spikePreAllocatedVUs: SPIKE_PRE_ALLOCATED_VUS,
  maxVUs: Math.max(PRE_ALLOCATED_VUS, CONFIGURED_MAX_VUS),
  spikeMaxVUs: Math.max(SPIKE_PRE_ALLOCATED_VUS, CONFIGURED_MAX_VUS),
});

export function setupOrderTest(testType, details = {}) {
  if (!AUTH_TOKEN) {
    throw new Error('AUTH_TOKEN is required. Export it or pass -e AUTH_TOKEN="$AUTH_TOKEN".');
  }

  validateJwt(AUTH_TOKEN, TENANT_ID);

  console.log(
    JSON.stringify({
      testType,
      baseUrl: BASE_URL,
      endpoints: SELECTED_ENDPOINTS.map((endpoint) => endpoint.path),
      aggregateTargetRate: true,
      safeTps: SAFE_TPS,
      maxVUs: config.maxVUs,
      ...details,
    }),
  );
}

export function executeOrderRequest() {
  const endpointIndex = exec.scenario.iterationInTest % SELECTED_ENDPOINTS.length;
  const endpoint = SELECTED_ENDPOINTS[endpointIndex];
  const tags = {
    endpoint: endpoint.name,
    route: endpoint.path,
  };
  const payload = JSON.stringify({
    productOptionId: endpoint.productOptionId,
    quantity: endpoint.quantity,
    requestUpdateVersion: endpoint.requestUpdateVersion,
  });

  const response = http.post(`${BASE_URL}${endpoint.path}`, payload, {
    headers: REQUEST_HEADERS,
    redirects: 0,
    responseCallback: http.expectedStatuses({ min: 200, max: 299 }),
    tags: {
      ...tags,
      name: endpoint.path,
    },
    timeout: HTTP_TIMEOUT,
  });

  const httpSucceeded = response.status >= 200 && response.status < 300;
  const businessSucceeded = endpoint.booleanResponse
    ? httpSucceeded && response.body.trim() === 'true'
    : httpSucceeded;

  check(
    response,
    {
      'HTTP status is 2xx': () => httpSucceeded,
      'inventory/order operation succeeded': () => businessSucceeded,
    },
    tags,
  );

  businessFailures.add(!businessSucceeded, tags);
  if (businessSucceeded) {
    businessSuccesses.add(1, tags);
  }
}

export function arrivalScenario(useSpikeCapacity = false) {
  return {
    exec: 'runOrderRequest',
    timeUnit: '1s',
    maxVUs: useSpikeCapacity ? config.spikeMaxVUs : config.maxVUs,
  };
}

export function standardThresholds() {
  return thresholdsForSelector('');
}

export function recoveryThresholds() {
  return thresholdsForSelector('phase:recovery');
}

export function createOrderSummary(
  data,
  testType,
  selector = '',
  scopedDurationMs,
) {
  const generatedAt = new Date();
  const timestamp = generatedAt.toISOString().replace(/[:.]/g, '-');
  const summaryDirectory = stringEnv(
    'SUMMARY_DIR',
    'load-tests/k6/results',
  ).replace(/\/+$/, '');
  const summaryPath = `${summaryDirectory}/${testType}-${timestamp}.json`;
  const output = {
    stdout: buildConsoleSummary(
      data,
      testType,
      generatedAt,
      selector,
      scopedDurationMs,
    ),
  };

  if (booleanEnv('SUMMARY_EXPORT', true)) {
    output[summaryPath] = JSON.stringify(data, null, 2);
  }

  return output;
}

function buildConsoleSummary(
  data,
  testType,
  generatedAt,
  selector,
  scopedDurationMs,
) {
  const metricSuffix = selector ? `{${selector}}` : '';
  const overallScope = selector ? `all (${selector})` : 'all';
  const testDurationMs = Number.isFinite(scopedDurationMs)
    ? scopedDurationMs
    : data.state && data.state.testRunDurationMs;
  const lines = [
    '',
    `=== Order k6 Summary: ${testType} ===`,
    `Generated: ${generatedAt.toISOString()}`,
    `Duration : ${formatDuration(data.state && data.state.testRunDurationMs)}`,
    '',
    summaryHeader(),
    summaryRow(overallScope, data.metrics, metricSuffix, true, testDurationMs),
  ];

  SELECTED_ENDPOINTS.forEach((endpoint) => {
    const endpointSelector = selector
      ? `${selector},endpoint:${endpoint.name}`
      : `endpoint:${endpoint.name}`;
    lines.push(
      summaryRow(
        endpoint.name,
        data.metrics,
        `{${endpointSelector}}`,
        false,
        testDurationMs,
      ),
    );
  });

  const failedThresholds = findFailedThresholds(data.metrics);
  lines.push('');
  if (failedThresholds.length === 0) {
    lines.push('Thresholds: PASS');
  } else {
    lines.push(`Thresholds: FAIL (${failedThresholds.length})`);
    failedThresholds.forEach((threshold) => lines.push(`  - ${threshold}`));
  }
  lines.push('');

  return `${lines.join('\n')}\n`;
}

function summaryHeader() {
  return [
    padRight('scope', 22),
    padLeft('requests', 10),
    padLeft('req/s', 10),
    padLeft('success', 10),
    padLeft('http fail', 11),
    padLeft('biz fail', 10),
    padLeft('avg', 10),
    padLeft('p95', 10),
    padLeft('dropped', 10),
  ].join(' ');
}

function summaryRow(scope, metrics, selector, includeDropped, testDurationMs) {
  const requests = metricValues(metrics, `http_reqs${selector}`);
  const successes = metricValues(metrics, `business_successes${selector}`);
  const httpFailures = metricValues(metrics, `http_req_failed${selector}`);
  const businessFailureValues = metricValues(
    metrics,
    `business_failures${selector}`,
  );
  const duration = metricValues(metrics, `http_req_duration${selector}`);
  const dropped = includeDropped
    ? metricValues(metrics, `dropped_iterations${selector}`)
    : null;
  const requestCount = Number.isFinite(requests.count)
    ? requests.count
    : rateSampleCount(httpFailures);
  const requestRate = Number.isFinite(requests.rate)
    ? requests.rate
    : deriveRequestRate(requestCount, testDurationMs);
  const successCount = Number.isFinite(successes.count)
    ? successes.count
    : deriveSuccessCount(requestCount, businessFailureValues);

  return [
    padRight(scope, 22),
    padLeft(formatCount(requestCount), 10),
    padLeft(formatRate(requestRate), 10),
    padLeft(formatCount(successCount), 10),
    padLeft(formatPercent(httpFailures.rate), 11),
    padLeft(formatPercent(businessFailureValues.rate), 10),
    padLeft(formatMilliseconds(duration.avg), 10),
    padLeft(formatMilliseconds(duration['p(95)']), 10),
    padLeft(dropped ? formatCount(dropped.count) : '-', 10),
  ].join(' ');
}

function metricValues(metrics, name) {
  return metrics[name] && metrics[name].values ? metrics[name].values : {};
}

function rateSampleCount(values) {
  if (!Number.isFinite(values.passes) || !Number.isFinite(values.fails)) {
    return undefined;
  }
  return values.passes + values.fails;
}

function deriveRequestRate(requestCount, testDurationMs) {
  if (!Number.isFinite(requestCount) || !Number.isFinite(testDurationMs)) {
    return undefined;
  }
  return requestCount / (testDurationMs / 1000);
}

function deriveSuccessCount(requestCount, businessFailureValues) {
  if (Number.isFinite(businessFailureValues.fails)) {
    return businessFailureValues.fails;
  }
  if (
    !Number.isFinite(requestCount) ||
    !Number.isFinite(businessFailureValues.rate)
  ) {
    return undefined;
  }
  return requestCount * (1 - businessFailureValues.rate);
}

function findFailedThresholds(metrics) {
  const failed = [];

  Object.keys(metrics).forEach((metricName) => {
    const thresholds = metrics[metricName].thresholds || {};
    Object.keys(thresholds).forEach((expression) => {
      if (thresholds[expression].ok === false) {
        failed.push(`${metricName}: ${expression}`);
      }
    });
  });

  return failed;
}

function formatDuration(milliseconds) {
  if (!Number.isFinite(milliseconds)) {
    return '-';
  }
  return `${(milliseconds / 1000).toFixed(2)}s`;
}

function formatMilliseconds(milliseconds) {
  if (!Number.isFinite(milliseconds)) {
    return '-';
  }
  return milliseconds >= 1000
    ? `${(milliseconds / 1000).toFixed(2)}s`
    : `${milliseconds.toFixed(2)}ms`;
}

function formatPercent(rate) {
  return Number.isFinite(rate) ? `${(rate * 100).toFixed(2)}%` : '-';
}

function formatRate(rate) {
  return Number.isFinite(rate) ? rate.toFixed(2) : '-';
}

function formatCount(count) {
  if (!Number.isFinite(count)) {
    return '-';
  }
  return String(Math.round(count)).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

function padLeft(value, width) {
  const text = String(value);
  return text.length >= width ? text : `${' '.repeat(width - text.length)}${text}`;
}

function padRight(value, width) {
  const text = String(value);
  return text.length >= width ? text : `${text}${' '.repeat(width - text.length)}`;
}

function thresholdsForSelector(selector) {
  const errorThreshold = `rate<${MAX_ERROR_RATE}`;
  const latencyThreshold = `p(95)<${P95_MS}`;
  const suffix = selector ? `{${selector}}` : '';
  const thresholds = {
    [`http_req_failed${suffix}`]: [errorThreshold],
    [`business_failures${suffix}`]: [errorThreshold],
    [`http_req_duration${suffix}`]: [latencyThreshold],
    [`dropped_iterations${suffix}`]: ['count==0'],
  };

  SELECTED_ENDPOINTS.forEach((endpoint) => {
    const endpointSelector = selector
      ? `${selector},endpoint:${endpoint.name}`
      : `endpoint:${endpoint.name}`;
    thresholds[`http_req_failed{${endpointSelector}}`] = [errorThreshold];
    thresholds[`business_failures{${endpointSelector}}`] = [errorThreshold];
    thresholds[`http_req_duration{${endpointSelector}}`] = [latencyThreshold];
  });

  return thresholds;
}

function selectEndpoints(rawValue) {
  const requested = rawValue
    .toLowerCase()
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
  const names = requested.includes('all')
    ? Object.keys(ENDPOINT_CATALOG)
    : [...new Set(requested)];

  if (names.length === 0) {
    throw new Error('ENDPOINTS must contain pessimistic, conditional, purchase, or all.');
  }

  return names.map((name) => {
    const endpoint = ENDPOINT_CATALOG[name];
    if (!endpoint) {
      throw new Error(
        `Unknown endpoint '${name}'. Use pessimistic, conditional, purchase, or all.`,
      );
    }
    return endpoint;
  });
}

function validateJwt(token, tenantId) {
  const tokenParts = token.split('.');
  if (tokenParts.length !== 3) {
    throw new Error('AUTH_TOKEN is not a JWT (expected three dot-separated parts).');
  }

  let payload;
  try {
    payload = JSON.parse(encoding.b64decode(tokenParts[1], 'rawurl', 's'));
  } catch (error) {
    throw new Error(`AUTH_TOKEN payload could not be decoded: ${error.message}`);
  }

  if (payload.exp && payload.exp * 1000 <= Date.now()) {
    throw new Error(
      `AUTH_TOKEN expired at ${new Date(payload.exp * 1000).toISOString()}.`,
    );
  }
  if (payload.tenantKey && payload.tenantKey !== tenantId) {
    throw new Error(
      `TENANT_ID '${tenantId}' does not match the JWT tenantKey '${payload.tenantKey}'.`,
    );
  }
}

export function stagesEnv(name, fallback) {
  const rawValue = __ENV[name];
  if (!rawValue) {
    return fallback;
  }

  return rawValue.split(',').map((stage, index) => {
    const separatorIndex = stage.lastIndexOf(':');
    if (separatorIndex <= 0) {
      throw new Error(
        `${name} stage #${index + 1} must use the format duration:target.`,
      );
    }

    const duration = stage.slice(0, separatorIndex).trim();
    const target = Number(stage.slice(separatorIndex + 1).trim());
    durationToSeconds(duration);
    if (!Number.isInteger(target) || target < 0) {
      throw new Error(`${name} stage #${index + 1} has an invalid target.`);
    }
    return { duration, target };
  });
}

export function durationToSeconds(value) {
  const duration = value.trim();
  const matcher = /(\d+(?:\.\d+)?)(ms|s|m|h)/g;
  const unitSeconds = { ms: 0.001, s: 1, m: 60, h: 3600 };
  let total = 0;
  let consumed = '';
  let match;

  while ((match = matcher.exec(duration)) !== null) {
    consumed += match[0];
    total += Number(match[1]) * unitSeconds[match[2]];
  }

  if (!duration || consumed !== duration || total <= 0) {
    throw new Error(`Invalid duration '${value}'. Examples: 30s, 2m, 1h30m.`);
  }
  return total;
}

export function secondsDuration(seconds) {
  return `${seconds}s`;
}

export function stringEnv(name, fallback) {
  const value = __ENV[name];
  return value === undefined || value === '' ? fallback : value.trim();
}

export function integerEnv(name, fallback, minimum) {
  const value = __ENV[name] === undefined ? fallback : Number(__ENV[name]);
  if (!Number.isInteger(value) || value < minimum) {
    throw new Error(`${name} must be an integer greater than or equal to ${minimum}.`);
  }
  return value;
}

function numberEnv(name, fallback, minimum, maximum) {
  const value = __ENV[name] === undefined ? fallback : Number(__ENV[name]);
  if (!Number.isFinite(value) || value < minimum || value >= maximum) {
    throw new Error(`${name} must be at least ${minimum} and less than ${maximum}.`);
  }
  return value;
}

function booleanEnv(name, fallback) {
  const value = __ENV[name];
  if (value === undefined || value === '') {
    return fallback;
  }
  if (value === 'true') {
    return true;
  }
  if (value === 'false') {
    return false;
  }
  throw new Error(`${name} must be true or false.`);
}
