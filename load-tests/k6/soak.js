import {
  arrivalScenario,
  config,
  createOrderSummary,
  executeOrderRequest,
  setupOrderTest,
  standardThresholds,
  stringEnv,
} from './common/order-test-common.js';

// 안전 TPS를 장시간 유지해 메모리/connection/GC 추세를 확인한다.
const SOAK_DURATION = stringEnv('SOAK_DURATION', '30m');

export const options = {
  discardResponseBodies: false,
  scenarios: {
    soak: {
      ...arrivalScenario(),
      executor: 'constant-arrival-rate',
      rate: config.safeTps,
      duration: SOAK_DURATION,
      preAllocatedVUs: config.preAllocatedVUs,
      tags: { test_type: 'soak', phase: 'soak' },
    },
  },
  thresholds: standardThresholds(),
};

export function setup() {
  setupOrderTest('soak', {
    duration: SOAK_DURATION,
  });
}

export function runOrderRequest() {
  executeOrderRequest();
}

export function handleSummary(data) {
  return createOrderSummary(data, 'soak');
}
