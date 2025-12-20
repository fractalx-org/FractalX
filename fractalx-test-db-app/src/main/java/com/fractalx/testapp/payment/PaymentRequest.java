package com.fractalx.testapp.payment;

public record PaymentRequest(
        String customerId,
        Double amount
) {}