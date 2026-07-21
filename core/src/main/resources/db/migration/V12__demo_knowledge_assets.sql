INSERT INTO raw_source_objects (
    id, organization_id, department_id, source_system, source_connection_key,
    external_object_id, source_version, object_type, title, raw_content, source_uri,
    payload_sha256, source_modified_at, classification, declared_access, status,
    created_at, updated_at, version
) VALUES
    (
        '21000000-0000-4000-8000-000000000001',
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222',
        'MANUAL', 'demo-seed', 'handbook', 'v1', 'DOCUMENT',
        'Employee handbook',
        'The employee handbook describes company-wide onboarding, conduct, leave, and support policies.',
        'https://source.example.test/docs/handbook',
        '47b2910ef45d01cbf27f73ca578cf2d4f9bb055032a7ab7d3b5a415d578f92d2', now(), 'PUBLIC', 'ALL', 'NORMALIZED', now(), now(), 0
    ),
    (
        '21000000-0000-4000-8000-000000000002',
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222',
        'MANUAL', 'demo-seed', 'sales-playbook', 'v1', 'DOCUMENT',
        'Sales qualification playbook',
        'The sales playbook contains confidential discovery questions, qualification thresholds, and escalation paths.',
        'https://source.example.test/docs/sales-playbook',
        '7c2bef8a47082d99176f90434966b17acbdc473075f9a47f1dff9658e898a5e6', now(), 'CONFIDENTIAL', 'OWN_DEPARTMENT', 'NORMALIZED', now(), now(), 0
    ),
    (
        '21000000-0000-4000-8000-000000000003',
        '11111111-1111-1111-1111-111111111111',
        '88888888-8888-8888-8888-888888888888',
        'MANUAL', 'demo-seed', 'marketing-plan', 'v1', 'DOCUMENT',
        'Marketing launch plan',
        'The launch plan contains confidential campaign timing, positioning, and channel budget assumptions.',
        'https://source.example.test/docs/marketing-plan',
        '8e018a624acbf9bd00feef543be694218f7cc94589e27e790d64a01dd585f99b', now(), 'CONFIDENTIAL', 'OWN_DEPARTMENT', 'NORMALIZED', now(), now(), 0
    ),
    (
        '21000000-0000-4000-8000-000000000004',
        '11111111-1111-1111-1111-111111111111',
        '99999999-9999-9999-9999-999999999999',
        'MANUAL', 'demo-seed', 'executive-plan', 'v1', 'DOCUMENT',
        'Executive restructuring plan',
        'The restricted plan contains executive-only restructuring scenarios and confidential decision dates.',
        'https://source.example.test/docs/executive-plan',
        'c66d4b85c1c821bbd1b2c503ae85689b1b525de34a0dd0e0bf3bdc4ff396a005', now(), 'RESTRICTED', 'EXECUTIVE_ONLY', 'NORMALIZED', now(), now(), 0
    ),
    (
        '21000000-0000-4000-8000-000000000005',
        '11111111-1111-1111-1111-111111111111',
        '99999999-9999-9999-9999-999999999999',
        'MANUAL', 'demo-seed', 'denied-policy', 'v1', 'DOCUMENT',
        'Operations incident policy',
        'This internal policy is source-denied for the seeded Sales user to prove explicit deny precedence.',
        'https://source.example.test/docs/denied-policy',
        'c299404464ffb4498004bd6a0eb9563f6a91b0500b667f7935545449973cc267', now(), 'INTERNAL', 'ALL_EMPLOYEES', 'NORMALIZED', now(), now(), 0
    );

INSERT INTO source_acl_snapshots (
    id, organization_id, raw_source_object_id, acl_generation, capture_status, default_gate,
    acl_sha256, captured_at, valid_until
) VALUES
    ('22000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111', '21000000-0000-4000-8000-000000000001', 1, 'COMPLETE', 'UNKNOWN', repeat('a', 64), now(), now() + interval '24 hours'),
    ('22000000-0000-4000-8000-000000000002', '11111111-1111-1111-1111-111111111111', '21000000-0000-4000-8000-000000000002', 1, 'COMPLETE', 'UNKNOWN', repeat('b', 64), now(), now() + interval '24 hours'),
    ('22000000-0000-4000-8000-000000000003', '11111111-1111-1111-1111-111111111111', '21000000-0000-4000-8000-000000000003', 1, 'COMPLETE', 'UNKNOWN', repeat('c', 64), now(), now() + interval '24 hours'),
    ('22000000-0000-4000-8000-000000000004', '11111111-1111-1111-1111-111111111111', '21000000-0000-4000-8000-000000000004', 1, 'COMPLETE', 'UNKNOWN', repeat('d', 64), now(), now() + interval '24 hours'),
    ('22000000-0000-4000-8000-000000000005', '11111111-1111-1111-1111-111111111111', '21000000-0000-4000-8000-000000000005', 1, 'COMPLETE', 'UNKNOWN', repeat('e', 64), now(), now() + interval '24 hours');

INSERT INTO source_acl_entries (
    id, organization_id, source_acl_snapshot_id, principal_type, principal_key, gate, created_at
) VALUES
    ('25000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111', '22000000-0000-4000-8000-000000000001', 'ORGMEMORY_ORGANIZATION', '11111111-1111-1111-1111-111111111111', 'ALLOW', now()),
    ('25000000-0000-4000-8000-000000000002', '11111111-1111-1111-1111-111111111111', '22000000-0000-4000-8000-000000000002', 'ORGMEMORY_DEPARTMENT', '22222222-2222-2222-2222-222222222222', 'ALLOW', now()),
    ('25000000-0000-4000-8000-000000000003', '11111111-1111-1111-1111-111111111111', '22000000-0000-4000-8000-000000000003', 'ORGMEMORY_DEPARTMENT', '88888888-8888-8888-8888-888888888888', 'ALLOW', now()),
    ('25000000-0000-4000-8000-000000000004', '11111111-1111-1111-1111-111111111111', '22000000-0000-4000-8000-000000000004', 'ORGMEMORY_ORGANIZATION', '11111111-1111-1111-1111-111111111111', 'ALLOW', now()),
    ('25000000-0000-4000-8000-000000000005', '11111111-1111-1111-1111-111111111111', '22000000-0000-4000-8000-000000000005', 'ORGMEMORY_ORGANIZATION', '11111111-1111-1111-1111-111111111111', 'ALLOW', now()),
    ('25000000-0000-4000-8000-000000000006', '11111111-1111-1111-1111-111111111111', '22000000-0000-4000-8000-000000000005', 'ORGMEMORY_USER', '44444444-4444-4444-4444-444444444444', 'DENY', now());

INSERT INTO source_acl_snapshot_seals (
    source_acl_snapshot_id, organization_id, entry_count, entries_sha256, sealed_at
) VALUES
    ('22000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111', 1, '0bc507f08c269d6a2526d542081bb8149ed3e58453f92fa1c69fc66599f97ad0', now()),
    ('22000000-0000-4000-8000-000000000002', '11111111-1111-1111-1111-111111111111', 1, 'd0f35b3f6d8b7d66656b8e521c670292e67d8f1004a90bc28c056e30eebfb040', now()),
    ('22000000-0000-4000-8000-000000000003', '11111111-1111-1111-1111-111111111111', 1, '23a24a6cc79caad2e1a08d18aca5f114cfb12b0d09f805a2a041c2101bd067bc', now()),
    ('22000000-0000-4000-8000-000000000004', '11111111-1111-1111-1111-111111111111', 1, '0bc507f08c269d6a2526d542081bb8149ed3e58453f92fa1c69fc66599f97ad0', now()),
    ('22000000-0000-4000-8000-000000000005', '11111111-1111-1111-1111-111111111111', 2, 'a8d496604e413aa01505a81d30499f75b998918518ebed69d69fd8fc133961a2', now());

INSERT INTO source_acl_heads (
    id, organization_id, source_system, source_connection_key, external_object_id,
    current_raw_source_object_id, current_snapshot_id, acl_generation,
    created_at, updated_at, version
) VALUES
    ('26000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111', 'MANUAL', 'demo-seed', 'handbook', '21000000-0000-4000-8000-000000000001', '22000000-0000-4000-8000-000000000001', 1, now(), now(), 0),
    ('26000000-0000-4000-8000-000000000002', '11111111-1111-1111-1111-111111111111', 'MANUAL', 'demo-seed', 'sales-playbook', '21000000-0000-4000-8000-000000000002', '22000000-0000-4000-8000-000000000002', 1, now(), now(), 0),
    ('26000000-0000-4000-8000-000000000003', '11111111-1111-1111-1111-111111111111', 'MANUAL', 'demo-seed', 'marketing-plan', '21000000-0000-4000-8000-000000000003', '22000000-0000-4000-8000-000000000003', 1, now(), now(), 0),
    ('26000000-0000-4000-8000-000000000004', '11111111-1111-1111-1111-111111111111', 'MANUAL', 'demo-seed', 'executive-plan', '21000000-0000-4000-8000-000000000004', '22000000-0000-4000-8000-000000000004', 1, now(), now(), 0),
    ('26000000-0000-4000-8000-000000000005', '11111111-1111-1111-1111-111111111111', 'MANUAL', 'demo-seed', 'denied-policy', '21000000-0000-4000-8000-000000000005', '22000000-0000-4000-8000-000000000005', 1, now(), now(), 0);

INSERT INTO normalized_records (
    id, organization_id, raw_source_object_id, source_acl_snapshot_id, normalizer_version,
    title, normalized_content, language, department_id, classification, declared_access,
    content_sha256, status, issue_code, created_at, updated_at, version
) VALUES
    ('23000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111', '21000000-0000-4000-8000-000000000001', '22000000-0000-4000-8000-000000000001', 'seed-v1', 'Employee handbook', 'The employee handbook describes company-wide onboarding, conduct, leave, and support policies.', 'en', '22222222-2222-2222-2222-222222222222', 'PUBLIC', 'ALL', '47b2910ef45d01cbf27f73ca578cf2d4f9bb055032a7ab7d3b5a415d578f92d2', 'PROMOTED', NULL, now(), now(), 0),
    ('23000000-0000-4000-8000-000000000002', '11111111-1111-1111-1111-111111111111', '21000000-0000-4000-8000-000000000002', '22000000-0000-4000-8000-000000000002', 'seed-v1', 'Sales qualification playbook', 'The sales playbook contains confidential discovery questions, qualification thresholds, and escalation paths.', 'en', '22222222-2222-2222-2222-222222222222', 'CONFIDENTIAL', 'OWN_DEPARTMENT', '7c2bef8a47082d99176f90434966b17acbdc473075f9a47f1dff9658e898a5e6', 'PROMOTED', NULL, now(), now(), 0),
    ('23000000-0000-4000-8000-000000000003', '11111111-1111-1111-1111-111111111111', '21000000-0000-4000-8000-000000000003', '22000000-0000-4000-8000-000000000003', 'seed-v1', 'Marketing launch plan', 'The launch plan contains confidential campaign timing, positioning, and channel budget assumptions.', 'en', '88888888-8888-8888-8888-888888888888', 'CONFIDENTIAL', 'OWN_DEPARTMENT', '8e018a624acbf9bd00feef543be694218f7cc94589e27e790d64a01dd585f99b', 'PROMOTED', NULL, now(), now(), 0),
    ('23000000-0000-4000-8000-000000000004', '11111111-1111-1111-1111-111111111111', '21000000-0000-4000-8000-000000000004', '22000000-0000-4000-8000-000000000004', 'seed-v1', 'Executive restructuring plan', 'The restricted plan contains executive-only restructuring scenarios and confidential decision dates.', 'en', '99999999-9999-9999-9999-999999999999', 'RESTRICTED', 'EXECUTIVE_ONLY', 'c66d4b85c1c821bbd1b2c503ae85689b1b525de34a0dd0e0bf3bdc4ff396a005', 'PROMOTED', NULL, now(), now(), 0),
    ('23000000-0000-4000-8000-000000000005', '11111111-1111-1111-1111-111111111111', '21000000-0000-4000-8000-000000000005', '22000000-0000-4000-8000-000000000005', 'seed-v1', 'Operations incident policy', 'This internal policy is source-denied for the seeded Sales user to prove explicit deny precedence.', 'en', '99999999-9999-9999-9999-999999999999', 'INTERNAL', 'ALL_EMPLOYEES', 'c299404464ffb4498004bd6a0eb9563f6a91b0500b667f7935545449973cc267', 'PROMOTED', NULL, now(), now(), 0);

INSERT INTO knowledge_assets (
    id, organization_id, raw_source_object_id, normalized_record_id, source_acl_snapshot_id,
    department_id, title, content, language, classification, declared_access, content_sha256,
    orgmemory_gate, status, activated_at, retired_at, created_at, updated_at, version
) VALUES
    ('24000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111', '21000000-0000-4000-8000-000000000001', '23000000-0000-4000-8000-000000000001', '22000000-0000-4000-8000-000000000001', '22222222-2222-2222-2222-222222222222', 'Employee handbook', 'The employee handbook describes company-wide onboarding, conduct, leave, and support policies.', 'en', 'PUBLIC', 'ALL', '47b2910ef45d01cbf27f73ca578cf2d4f9bb055032a7ab7d3b5a415d578f92d2', 'ALLOW', 'ACTIVE', now(), NULL, now(), now(), 0),
    ('24000000-0000-4000-8000-000000000002', '11111111-1111-1111-1111-111111111111', '21000000-0000-4000-8000-000000000002', '23000000-0000-4000-8000-000000000002', '22000000-0000-4000-8000-000000000002', '22222222-2222-2222-2222-222222222222', 'Sales qualification playbook', 'The sales playbook contains confidential discovery questions, qualification thresholds, and escalation paths.', 'en', 'CONFIDENTIAL', 'OWN_DEPARTMENT', '7c2bef8a47082d99176f90434966b17acbdc473075f9a47f1dff9658e898a5e6', 'ALLOW', 'ACTIVE', now(), NULL, now(), now(), 0),
    ('24000000-0000-4000-8000-000000000003', '11111111-1111-1111-1111-111111111111', '21000000-0000-4000-8000-000000000003', '23000000-0000-4000-8000-000000000003', '22000000-0000-4000-8000-000000000003', '88888888-8888-8888-8888-888888888888', 'Marketing launch plan', 'The launch plan contains confidential campaign timing, positioning, and channel budget assumptions.', 'en', 'CONFIDENTIAL', 'OWN_DEPARTMENT', '8e018a624acbf9bd00feef543be694218f7cc94589e27e790d64a01dd585f99b', 'ALLOW', 'ACTIVE', now(), NULL, now(), now(), 0),
    ('24000000-0000-4000-8000-000000000004', '11111111-1111-1111-1111-111111111111', '21000000-0000-4000-8000-000000000004', '23000000-0000-4000-8000-000000000004', '22000000-0000-4000-8000-000000000004', '99999999-9999-9999-9999-999999999999', 'Executive restructuring plan', 'The restricted plan contains executive-only restructuring scenarios and confidential decision dates.', 'en', 'RESTRICTED', 'EXECUTIVE_ONLY', 'c66d4b85c1c821bbd1b2c503ae85689b1b525de34a0dd0e0bf3bdc4ff396a005', 'ALLOW', 'ACTIVE', now(), NULL, now(), now(), 0),
    ('24000000-0000-4000-8000-000000000005', '11111111-1111-1111-1111-111111111111', '21000000-0000-4000-8000-000000000005', '23000000-0000-4000-8000-000000000005', '22000000-0000-4000-8000-000000000005', '99999999-9999-9999-9999-999999999999', 'Operations incident policy', 'This internal policy is source-denied for the seeded Sales user to prove explicit deny precedence.', 'en', 'INTERNAL', 'ALL_EMPLOYEES', 'c299404464ffb4498004bd6a0eb9563f6a91b0500b667f7935545449973cc267', 'ALLOW', 'ACTIVE', now(), NULL, now(), now(), 0);
