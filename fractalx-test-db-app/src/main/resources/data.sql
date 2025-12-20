-- Create some dummy payments so we can test immediately
INSERT INTO payments (transaction_id, customer_id, amount, status) VALUES ('txn-100', 'cust-1', 50.00, 'SUCCESS');
INSERT INTO payments (transaction_id, customer_id, amount, status) VALUES ('txn-101', 'cust-2', 200.00, 'SUCCESS');