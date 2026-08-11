package com.msa.product.entity.dlq;

/**
 * DLQ 발송 정보 DB 상태 값 구현
 */
public enum DlqStatus {
    NEW,        // 적재됨, 아직 처리 안 함
    REPLAYING,  // 재처리 중
    REPLAYED,   // 재처리 성공
    FAILED,     // 재처리 시도했으나 실패
    IGNORED     // 운영자가 무시 처리
}