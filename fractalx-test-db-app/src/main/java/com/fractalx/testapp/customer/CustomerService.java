package com.fractalx.testapp.customer;

import com.fractalx.annotations.DecomposableModule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
// THIS ANNOTATION DEFINES THE MICROSERVICE BOUNDARY
@DecomposableModule(
        serviceName = "customer-service",
        port = 8082,
        ownedSchemas = {"customers"}
)
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        Customer customer = new Customer();
        customer.setName(request.name());
        customer.setEmail(request.email());

        customerRepository.save(customer);

        return new CustomerResponse(customer.getId(), customer.getName(), customer.getEmail());
    }

    public CustomerResponse getCustomer(String id) {
        Optional<Customer> customer = customerRepository.findById(id);
        if (customer.isPresent()) {
            Customer c = customer.get();
            return new CustomerResponse(c.getId(), c.getName(), c.getEmail());
        }
        throw new RuntimeException("Customer not found: " + id);
    }
}