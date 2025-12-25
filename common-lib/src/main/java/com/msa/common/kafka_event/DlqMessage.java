package com.msa.common.kafka_event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 실패 정보 보존용 컨테이너
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DlqMessage<T> {

    private String originalTopic;
    private int originalPartition;
    private long originalOffset;

    private String exceptionClass;
    private String exceptionMessage;
    private String stackTrace;

    private T payload;
}
