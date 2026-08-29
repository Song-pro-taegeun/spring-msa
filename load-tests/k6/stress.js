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

// Ramp-up에서 찾은 한계를 넘겨 실패 양상과 안전 TPS 복귀를 확인한다.
const LIMIT_TPS = integerEnv('LIMIT_TPS', 2000, 1);
const STRESS_STAGES = stagesEnv('STRESS_STAGES', [
  { duration: '1m', target: LIMIT_TPS },
  { duration: '2m', target: LIMIT_TPS },
  { duration: '30s', target: Math.ceil(LIMIT_TPS * 1.25) },
  { duration: '2m', target: Math.ceil(LIMIT_TPS * 1.25) },
  { duration: '30s', target: Math.ceil(LIMIT_TPS * 1.5) },
  { duration: '2m', target: Math.ceil(LIMIT_TPS * 1.5) },
  { duration: '30s', target: LIMIT_TPS * 2 },
  { duration: '2m', target: LIMIT_TPS * 2 },
  { duration: '30s', target: config.safeTps },
  { duration: '1m', target: config.safeTps },
  { duration: '30s', target: 0 },
]);

export const options = {
  discardResponseBodies: false,
  scenarios: {
    stress: {
      ...arrivalScenario(),
      executor: 'ramping-arrival-rate',
      startRate: config.safeTps,
      preAllocatedVUs: config.preAllocatedVUs,
      stages: STRESS_STAGES,
      tags: { test_type: 'stress', phase: 'stress' },
    },
  },
  thresholds: standardThresholds(),
};

export function setup() {
  setupOrderTest('stress', {
    limitTps: LIMIT_TPS,
    stages: STRESS_STAGES,
  });
}

export function runOrderRequest() {
  executeOrderRequest();
}

export function handleSummary(data) {
  return createOrderSummary(data, 'stress');
}
