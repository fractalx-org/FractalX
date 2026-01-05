package com.fractalx.testapp.customer;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public CustomerResponse create(@RequestBody CustomerRequest request) {
        return customerService.createCustomer(request);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable String id) {
        return customerService.getCustomer(id);
    }

    @GetMapping("/health")
    public String health() {
        return "Customer Service is running";
    }
}