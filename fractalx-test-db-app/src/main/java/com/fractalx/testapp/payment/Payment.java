package com.fractalx.testapp.payment;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    private String transactionId;
    private String customerId;
    private Double amount;
    private String status;

    public Payment() {
        this.transactionId = java.util.UUID.randomUUID().toString();
    }

    // Getters and Setters
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String id) { this.transactionId = id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String id) { this.customerId = id; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amt) { this.amount = amt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}