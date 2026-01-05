package com.fractalx.testapp.order;

/**
 * Request DTO for creating an order
 */
public record OrderRequest(
        String customerId,
        Double amount
) {}