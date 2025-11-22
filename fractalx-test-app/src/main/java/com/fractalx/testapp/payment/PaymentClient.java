package com.fractalx.testapp.payment;

// This interface will be used to generate REST client
public interface PaymentClient {
    boolean processPayment(String customerId, Double amount);
}