package com.fractalx.testapp.order;

import com.fractalx.annotations.DecomposableModule;
import com.fractalx.annotations.ServiceBoundary;
import com.fractalx.testapp.payment.PaymentClient;
import org.springframework.stereotype.Service;

@Service
@DecomposableModule(
        serviceName = "order-service",
        port = 8081,
        ownedSchemas = {"orders"}
)
@ServiceBoundary(allowedCallers = {"payment", "inventory"})
public class OrderService {

    private final PaymentClient paymentClient;

    public OrderService(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    public OrderResponse createOrder(OrderRequest request) {
        // Validate order
        Order order = new Order();
        order.setCustomerId(request.customerId());
        order.setAmount(request.amount());

        // This cross-module call will be transformed to REST
        boolean paymentSuccess = paymentClient.processPayment(
                request.customerId(),
                request.amount()
        );

        if (paymentSuccess) {
            order.setStatus("CONFIRMED");
        } else {
            order.setStatus("FAILED");
        }

        return new OrderResponse(order.getId(), order.getStatus());
    }
}