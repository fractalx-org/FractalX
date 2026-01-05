package com.fractalx.testapp.order;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import com.fractalx.testapp.customer.Customer;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    private String id;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private Double amount;
    private String status;

    public Order() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amt) { this.amount = amt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}