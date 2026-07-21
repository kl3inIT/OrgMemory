ALTER TABLE external_identities DROP COLUMN linked_email;

INSERT INTO external_identities (
    id, app_user_id, issuer, subject, created_at, updated_at, version
) VALUES
    (
        '31000000-0000-4000-8000-000000000001',
        '44444444-4444-4444-4444-444444444444',
        'http://localhost:8180/realms/orgmemory',
        '44444444-4444-4444-4444-444444444444',
        now(), now(), 0
    ),
    (
        '31000000-0000-4000-8000-000000000002',
        '55555555-5555-5555-5555-555555555555',
        'http://localhost:8180/realms/orgmemory',
        '55555555-5555-5555-5555-555555555555',
        now(), now(), 0
    ),
    (
        '31000000-0000-4000-8000-000000000003',
        '13000000-0000-4000-8000-000000000001',
        'http://localhost:8180/realms/orgmemory',
        '13000000-0000-4000-8000-000000000001',
        now(), now(), 0
    )
ON CONFLICT DO NOTHING;
