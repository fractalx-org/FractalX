package com.fractalx.testapp.order;

/**
 * Response DTO for order creation
 */
public record OrderResponse(
        String orderId,
        String status
) {}