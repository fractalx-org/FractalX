package com.fractalx.testapp.order;

import com.fractalx.annotations.DecomposableModule;
import com.fractalx.annotations.ServiceBoundary;
import com.fractalx.testapp.payment.PaymentClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fractalx.testapp.customer.Customer;

@Service
@DecomposableModule(serviceName = "order-service", port = 8081, ownedSchemas = {"orders"})
@ServiceBoundary(allowedCallers = {"payment", "inventory"})
public class OrderService {

    private final PaymentClient paymentClient;
    private final OrderRepository orderRepository;

    public OrderService(PaymentClient paymentClient, OrderRepository orderRepository) {
        this.paymentClient = paymentClient;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        // 1. Create Order (PENDING)
        Order order = new Order();

        Customer customer = new Customer();
        customer.setId(request.customerId());
        order.setCustomer(customer);

        order.setAmount(request.amount());
        order.setStatus("PENDING");
        orderRepository.save(order); // Save to DB

        // 2. Call Payment
        boolean paymentSuccess = paymentClient.processPayment(
                request.customerId(),
                request.amount()
        );

        // 3. Saga Logic (The Fix)
        if (paymentSuccess) {
            order.setStatus("CONFIRMED");
        } else {
            // COMPENSATING ACTION: Payment failed, so we Cancel the Order
            order.setStatus("CANCELLED");
        }

        orderRepository.save(order); // Update final status in DB

        return new OrderResponse(order.getId(), order.getStatus());
    }
}