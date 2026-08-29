import {
  arrivalScenario,
  config,
  createOrderSummary,
  executeOrderRequest,
  integerEnv,
  setupOrderTest,
  stagesEnv,
  standardThresholds,
} from './common/order-test-common.js';

// 2분 동안 총 TPS를 500까지 단계적으로 높여 처리량 한계를 찾는다.
const RAMP_START_TPS = integerEnv('RAMP_START_TPS', 50, 0);
const RAMP_STAGES = stagesEnv('RAMP_STAGES', [
  { duration: '10s', target: 100 },
  { duration: '10s', target: 100 },
  { duration: '10s', target: 200 },
  { duration: '10s', target: 200 },
  { duration: '10s', target: 300 },
  { duration: '10s', target: 300 },
  { duration: '10s', target: 400 },
  { duration: '10s', target: 400 },
  { duration: '10s', target: 500 },
  { duration: '10s', target: 500 },
  { duration: '20s', target: 0 },
]);

export const options = {
  discardResponseBodies: false,
  scenarios: {
    ramp_up: {
      ...arrivalScenario(),
      executor: 'ramping-arrival-rate',
      gracefulStop: '0s',
      startRate: RAMP_START_TPS,
      preAllocatedVUs: config.preAllocatedVUs,
      stages: RAMP_STAGES,
      tags: { test_type: 'ramp-up', phase: 'ramp-up' },
    },
  },
  thresholds: standardThresholds(),
};

export function setup() {
  setupOrderTest('ramp-up', {
    startTps: RAMP_START_TPS,
    stages: RAMP_STAGES,
  });
}

export function runOrderRequest() {
  executeOrderRequest();
}

export function handleSummary(data) {
  return createOrderSummary(data, 'ramp-up');
}
