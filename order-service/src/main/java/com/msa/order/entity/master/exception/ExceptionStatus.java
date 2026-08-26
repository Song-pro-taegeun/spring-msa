package com.msa.order.entity.master.exception;

public enum ExceptionStatus {
    NEW,           // 새로 발생한 예외
    ACKNOWLEDGED,  // 관리자가 확인
    CLOSED,        // 원인 확인 및 조치 완료
    IGNORED        // 확인 후 별도 조치하지 않음
}
