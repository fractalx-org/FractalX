package com.fractalx.testapp.payment;

import com.fractalx.annotations.DecomposableModule;
import com.fractalx.annotations.ServiceBoundary;
import org.springframework.stereotype.Service;

@Service
@DecomposableModule(
        serviceName = "payment-service",
        port = 8080,
        ownedSchemas = {"payments"}
)
@ServiceBoundary(strict = true)
public class PaymentService {

    public boolean processPayment(String customerId, Double amount) {
        // Payment processing logic
        System.out.println("Processing payment: " + amount + " for customer: " + customerId);
        return amount > 0 && amount < 10000; // Simple validation
    }
}