package com.msa.common.dto;

public record CommonRequestProvisionReplayDlqDto(
        String topic,
        Integer partition,
        Long offset
){

}
