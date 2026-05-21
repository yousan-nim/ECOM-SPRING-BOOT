-- ┌───────────────────────────────────────────────────────────┐
-- │ Repeatable dev seed.                                      │
-- │                                                           │
-- │ This file is ONLY loaded when the local/dev profile is    │
-- │ active (see application-local.yml / application-dev.yml). │
-- │ Production never sees it.                                 │
-- │                                                           │
-- │ All inserts use ON CONFLICT DO NOTHING so this can be     │
-- │ replayed safely.                                          │
-- └───────────────────────────────────────────────────────────┘

-- ─── Users ───────────────────────────────────────────────────
-- Password for all dev users is 'password123' (bcrypt hash below).
INSERT INTO users (id, email, password_hash, full_name, status, email_verified)
VALUES
    (1, 'admin@ecom.local',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Platform Admin', 'ACTIVE', TRUE),
    (2, 'vendor1@ecom.local','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Vendor One Co., Ltd.', 'ACTIVE', TRUE),
    (3, 'vendor2@ecom.local','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Vendor Two Shop',      'ACTIVE', TRUE),
    (4, 'alice@ecom.local',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Alice Customer', 'ACTIVE', TRUE),
    (5, 'bob@ecom.local',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Bob Customer',   'ACTIVE', TRUE)
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('users','id'), GREATEST((SELECT MAX(id) FROM users), 1));

INSERT INTO user_roles (user_id, role) VALUES
    (1, 'PLATFORM_ADMIN'),
    (2, 'VENDOR_ADMIN'), (2, 'CUSTOMER'),
    (3, 'VENDOR_ADMIN'), (3, 'CUSTOMER'),
    (4, 'CUSTOMER'),
    (5, 'CUSTOMER')
ON CONFLICT DO NOTHING;

-- ─── Vendors ─────────────────────────────────────────────────
INSERT INTO vendors (id, owner_user_id, name, slug, description, contact_email, contact_phone, status, default_commission_bps, payout_currency, onboarded_at)
VALUES
    (1, 2, 'Bangkok Electronics', 'bangkok-electronics', 'Top-tier consumer electronics in BKK.',
        'shop@vendor1.local', '+66-2-555-0001', 'ACTIVE', 1000, 'THB', NOW()),
    (2, 3, 'Chiang Mai Crafts',   'chiangmai-crafts',   'Hand-made northern Thai crafts.',
        'hello@vendor2.local','+66-53-555-0002', 'ACTIVE', 1200, 'THB', NOW())
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('vendors','id'), GREATEST((SELECT MAX(id) FROM vendors), 1));

-- ─── Categories ──────────────────────────────────────────────
INSERT INTO categories (id, parent_id, name, slug, path, depth, position)
VALUES
    (1, NULL, 'Electronics', 'electronics', '/electronics/', 0, 1),
    (2, 1,    'Phones',      'phones',      '/electronics/phones/', 1, 1),
    (3, 1,    'Audio',       'audio',       '/electronics/audio/',  1, 2),
    (4, NULL, 'Crafts',      'crafts',      '/crafts/',             0, 2),
    (5, 4,    'Textiles',    'textiles',    '/crafts/textiles/',    1, 1)
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('categories','id'), GREATEST((SELECT MAX(id) FROM categories), 1));

-- ─── Products + variants ─────────────────────────────────────
INSERT INTO products (id, vendor_id, category_id, name, slug, short_desc, description, status, price_min_amount, price_max_amount, price_currency, published_at)
VALUES
    (1, 1, 2, 'Demo Smartphone X100', 'demo-smartphone-x100',
        'A solid mid-range demo device.', 'Specs go here.', 'ACTIVE',
        1499000, 1799000, 'THB', NOW()),
    (2, 2, 5, 'Hand-woven Cotton Scarf', 'handwoven-cotton-scarf',
        'Hand-loomed in Chiang Mai.', 'Natural-dye cotton, 30x180cm.', 'ACTIVE',
        49000, 69000, 'THB', NOW())
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('products','id'), GREATEST((SELECT MAX(id) FROM products), 1));

-- Options
INSERT INTO product_options (id, product_id, name, position) VALUES
    (1, 1, 'Storage', 1),
    (2, 1, 'Color',   2),
    (3, 2, 'Color',   1)
ON CONFLICT DO NOTHING;
SELECT setval(pg_get_serial_sequence('product_options','id'), GREATEST((SELECT MAX(id) FROM product_options), 1));

INSERT INTO option_values (id, option_id, value, swatch_hex, position) VALUES
    (1, 1, '128GB', NULL, 1),
    (2, 1, '256GB', NULL, 2),
    (3, 2, 'Black', '#000000', 1),
    (4, 2, 'Silver','#C0C0C0', 2),
    (5, 3, 'Indigo','#3F51B5', 1),
    (6, 3, 'Crimson','#DC143C', 2)
ON CONFLICT DO NOTHING;
SELECT setval(pg_get_serial_sequence('option_values','id'), GREATEST((SELECT MAX(id) FROM option_values), 1));

-- Variants
INSERT INTO product_variants (id, product_id, sku, name_suffix, price_amount, price_currency, status) VALUES
    (1, 1, 'X100-128-BLK', '128GB / Black',  1499000, 'THB', 'ACTIVE'),
    (2, 1, 'X100-128-SLV', '128GB / Silver', 1499000, 'THB', 'ACTIVE'),
    (3, 1, 'X100-256-BLK', '256GB / Black',  1799000, 'THB', 'ACTIVE'),
    (4, 1, 'X100-256-SLV', '256GB / Silver', 1799000, 'THB', 'ACTIVE'),
    (5, 2, 'SCARF-IND',    'Indigo',           49000, 'THB', 'ACTIVE'),
    (6, 2, 'SCARF-CRM',    'Crimson',          69000, 'THB', 'ACTIVE')
ON CONFLICT (sku) DO NOTHING;
SELECT setval(pg_get_serial_sequence('product_variants','id'), GREATEST((SELECT MAX(id) FROM product_variants), 1));

INSERT INTO variant_option_values (variant_id, option_value_id) VALUES
    (1, 1), (1, 3),   -- 128GB Black
    (2, 1), (2, 4),   -- 128GB Silver
    (3, 2), (3, 3),   -- 256GB Black
    (4, 2), (4, 4),   -- 256GB Silver
    (5, 5),
    (6, 6)
ON CONFLICT DO NOTHING;

-- ─── Inventory ───────────────────────────────────────────────
INSERT INTO warehouses (id, vendor_id, name, is_default, is_active) VALUES
    (1, 1, 'BKK Main', TRUE, TRUE),
    (2, 2, 'CNX Studio', TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('warehouses','id'), GREATEST((SELECT MAX(id) FROM warehouses), 1));

INSERT INTO inventory (variant_id, warehouse_id, on_hand_qty, reserved_qty, safety_stock, reorder_point) VALUES
    (1, 1, 50, 0, 5, 10),
    (2, 1, 30, 0, 5, 10),
    (3, 1, 20, 0, 3,  5),
    (4, 1, 25, 0, 3,  5),
    (5, 2, 100, 0, 10, 20),
    (6, 2, 100, 0, 10, 20)
ON CONFLICT (variant_id, warehouse_id) DO NOTHING;

-- ─── Exchange rates (THB ↔ USD/JPY) ──────────────────────────
INSERT INTO exchange_rates (base_currency, quote_currency, rate, source, valid_from)
VALUES
    ('THB','USD', 0.0285, 'MANUAL', NOW()),
    ('THB','JPY', 4.25,   'MANUAL', NOW())
ON CONFLICT DO NOTHING;
