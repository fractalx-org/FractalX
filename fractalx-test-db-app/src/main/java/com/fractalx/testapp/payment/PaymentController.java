package com.fractalx.testapp.payment;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/process")
    public PaymentResponse processPayment(@RequestBody PaymentRequest request) {
        boolean success = paymentService.processPayment(
                request.customerId(),
                request.amount()
        );
        return new PaymentResponse(success, success ? "Payment processed" : "Payment failed");
    }

    @GetMapping("/health")
    public String health() {
        return "Payment Service is running";
    }
}