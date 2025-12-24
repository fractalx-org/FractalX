-- 1. Create Customers FIRST (referenced by Orders)
-- Using 'INSERT IGNORE' prevents errors if you restart the app (it won't crash on duplicates)
INSERT IGNORE INTO customers (id, name, email) VALUES ('cust-1', 'Alice', 'alice@test.com');
INSERT IGNORE INTO customers (id, name, email) VALUES ('cust-2', 'Bob', 'bob@test.com');
       INSERT IGNORE INTO customers (id, name, email) VALUES ('cust-25', 'Bob_1', 'bob1@test.com');

-- 2. Create Dummy Payments (for your PaymentClient to find)
INSERT IGNORE INTO payments (transaction_id, customer_id, amount, status) VALUES ('txn-100', 'cust-1', 50.00, 'SUCCESS');
INSERT IGNORE INTO payments (transaction_id, customer_id, amount, status) VALUES ('txn-101', 'cust-2', 200.00, 'SUCCESS');