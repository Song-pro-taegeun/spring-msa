package com.msa.order.dto;

public record OrderAcceptedResponse(
        String eventId,
        String status
) {
}