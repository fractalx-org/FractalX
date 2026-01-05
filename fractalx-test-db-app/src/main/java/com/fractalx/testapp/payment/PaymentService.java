package com.fractalx.testapp.payment;

import com.fractalx.annotations.DecomposableModule;
import com.fractalx.annotations.ServiceBoundary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@DecomposableModule(serviceName = "payment-service", port = 8080, ownedSchemas = {"payments"})
@ServiceBoundary(strict = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public boolean processPayment(String customerId, Double amount) {
        Payment payment = new Payment();
        payment.setCustomerId(customerId);
        payment.setAmount(amount);

        // LOGIC: If amount > 5000, we FAIL the payment.
        if (amount > 5000) {
            payment.setStatus("FAILED");
            paymentRepository.save(payment);
            return false;
        } else {
            payment.setStatus("SUCCESS");
            paymentRepository.save(payment);
            return true;
        }
    }
}