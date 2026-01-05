package com.fractalx.testapp.order;

import com.fractalx.testapp.payment.PaymentClient;
import org.springframework.stereotype.Component;

/**
 * Implementation of PaymentClient for monolithic mode
 * This will be replaced with REST client in microservices mode
 */
@Component
public class PaymentClientImpl implements PaymentClient {

    private final com.fractalx.testapp.payment.PaymentService paymentService;

    public PaymentClientImpl(com.fractalx.testapp.payment.PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public boolean processPayment(String customerId, Double amount) {
        return paymentService.processPayment(customerId, amount);
    }
}