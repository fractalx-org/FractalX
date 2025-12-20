package com.fractalx.testapp.order;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    private String id;
    private String customerId;
    private Double amount;
    private String status;

    public Order() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String id) { this.customerId = id; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amt) { this.amount = amt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}