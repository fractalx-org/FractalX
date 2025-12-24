package com.fractalx.testapp.order;

import jakarta.persistence.*;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    private String id;
    private String productName;
    private Double price;

    @ManyToOne
    @JoinColumn(name = "order_id") // We expect a REAL Foreign Key for this
    private Order order;

    public OrderItem() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    // Getters/setters
    public String getId() {
        return id;
    }
    public String getProductName() {
        return productName;
    }
    public void setProductName(String productName) {
        this.productName = productName;
    }
    public Double getPrice() {
        return price;
    }
    public void setPrice(Double price) {
        this.price = price;
    }
    public Order getOrder() {
        return order;
    }
    public void setOrder(Order order) {
        this.order = order;
    }

}