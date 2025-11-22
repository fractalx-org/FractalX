package com.fractalx.testapp.payment;

public record PaymentResponse(
        boolean success,
        String message
) {}