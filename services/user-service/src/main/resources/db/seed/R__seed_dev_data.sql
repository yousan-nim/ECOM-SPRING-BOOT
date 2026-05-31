-- ┌───────────────────────────────────────────────────────────┐
-- │ Repeatable dev seed (user-service).                       │
-- │                                                           │
-- │ Loaded ONLY when the local/dev profile is active          │
-- │ (see application-local.yml). Production never sees it.     │
-- │                                                           │
-- │ All inserts use ON CONFLICT DO NOTHING so this can be     │
-- │ replayed safely.                                          │
-- └───────────────────────────────────────────────────────────┘

-- ─── Users ───────────────────────────────────────────────────
-- Dev password for ALL seed users is 'Password1!' (BCrypt $2y$12$ — accepted by Spring's BCryptPasswordEncoder).
-- DO NOT use these in production.
INSERT INTO users (id, email, password_hash, full_name, status, email_verified)
VALUES
    (1, 'admin@ecom.local',  '$2y$12$uY/301w6CtpnblLSAx3pwe6RqWXl5K82dvXTsCJlJ8/yhMEKWi34C', 'Platform Admin', 'ACTIVE', TRUE),
    (2, 'vendor1@ecom.local','$2y$12$uY/301w6CtpnblLSAx3pwe6RqWXl5K82dvXTsCJlJ8/yhMEKWi34C', 'Vendor One Co., Ltd.', 'ACTIVE', TRUE),
    (3, 'vendor2@ecom.local','$2y$12$uY/301w6CtpnblLSAx3pwe6RqWXl5K82dvXTsCJlJ8/yhMEKWi34C', 'Vendor Two Shop',      'ACTIVE', TRUE),
    (4, 'alice@ecom.local',  '$2y$12$uY/301w6CtpnblLSAx3pwe6RqWXl5K82dvXTsCJlJ8/yhMEKWi34C', 'Alice Customer', 'ACTIVE', TRUE),
    (5, 'bob@ecom.local',    '$2y$12$uY/301w6CtpnblLSAx3pwe6RqWXl5K82dvXTsCJlJ8/yhMEKWi34C', 'Bob Customer',   'ACTIVE', TRUE)
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('users','id'), GREATEST((SELECT MAX(id) FROM users), 1));

INSERT INTO user_roles (user_id, role) VALUES
    (1, 'PLATFORM_ADMIN'),
    (2, 'VENDOR_ADMIN'), (2, 'CUSTOMER'),
    (3, 'VENDOR_ADMIN'), (3, 'CUSTOMER'),
    (4, 'CUSTOMER'),
    (5, 'CUSTOMER')
ON CONFLICT DO NOTHING;
