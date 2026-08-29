import {
  arrivalScenario,
  config,
  createOrderSummary,
  durationToSeconds,
  executeOrderRequest,
  recoveryThresholds,
  secondsDuration,
  setupOrderTest,
  stringEnv,
} from './common/order-test-common.js';

// 안전 TPS -> 순간 5,000 TPS -> 안전 TPS로 즉시 전환해 복구 여부를 본다.
const WARMUP_DURATION = stringEnv('SPIKE_WARMUP_DURATION', '2m');
const PEAK_DURATION = stringEnv('SPIKE_DURATION', '30s');
const RECOVERY_DURATION = stringEnv('SPIKE_RECOVERY_DURATION', '2m');

const PEAK_START_TIME = secondsDuration(durationToSeconds(WARMUP_DURATION));
const RECOVERY_START_TIME = secondsDuration(
  durationToSeconds(WARMUP_DURATION) + durationToSeconds(PEAK_DURATION),
);

export const options = {
  discardResponseBodies: false,
  scenarios: {
    warmup: {
      ...arrivalScenario(),
      executor: 'constant-arrival-rate',
      rate: config.safeTps,
      duration: WARMUP_DURATION,
      preAllocatedVUs: config.preAllocatedVUs,
      tags: { test_type: 'spike', phase: 'warmup' },
    },
    peak: {
      ...arrivalScenario(true),
      executor: 'constant-arrival-rate',
      rate: config.spikeTps,
      duration: PEAK_DURATION,
      startTime: PEAK_START_TIME,
      preAllocatedVUs: config.spikePreAllocatedVUs,
      tags: { test_type: 'spike', phase: 'peak' },
    },
    recovery: {
      ...arrivalScenario(),
      executor: 'constant-arrival-rate',
      rate: config.safeTps,
      duration: RECOVERY_DURATION,
      startTime: RECOVERY_START_TIME,
      preAllocatedVUs: config.preAllocatedVUs,
      tags: { test_type: 'spike', phase: 'recovery' },
    },
  },
  // Peak에서는 실패할 수 있으므로, 복구 구간에만 SLO threshold를 적용한다.
  thresholds: recoveryThresholds(),
};

export function setup() {
  setupOrderTest('spike', {
    spikeTps: config.spikeTps,
    warmupDuration: WARMUP_DURATION,
    peakDuration: PEAK_DURATION,
    recoveryDuration: RECOVERY_DURATION,
    maxVUs: config.spikeMaxVUs,
  });
}

export function runOrderRequest() {
  executeOrderRequest();
}

export function handleSummary(data) {
  return createOrderSummary(
    data,
    'spike',
    'phase:recovery',
    durationToSeconds(RECOVERY_DURATION) * 1000,
  );
}
