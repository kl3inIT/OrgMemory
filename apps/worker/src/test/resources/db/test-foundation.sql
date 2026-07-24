INSERT INTO organizations (
    id, name, created_at, updated_at, version
) VALUES (
    '11111111-1111-1111-1111-111111111111',
    'Worker integration test organization',
    now(),
    now(),
    0
) ON CONFLICT (id) DO NOTHING;

INSERT INTO departments (
    id, organization_id, name, created_at, updated_at, version
) VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'Sales',
    now(),
    now(),
    0
) ON CONFLICT (id) DO NOTHING;

INSERT INTO app_users (
    id, organization_id, department_id, name, email, role, active,
    created_at, updated_at, version
) VALUES (
    '44444444-4444-4444-4444-444444444444',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'Linh Nguyen',
    'linh@example.test',
    'EMPLOYEE',
    true,
    now(),
    now(),
    0
) ON CONFLICT (id) DO NOTHING;

INSERT INTO knowledge_spaces (
    id, organization_id, department_id, space_key, name, active,
    created_at, updated_at, version
) VALUES (
    '88888888-8888-4888-8888-888888888802',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'sales',
    'Sales Knowledge',
    true,
    now(),
    now(),
    0
) ON CONFLICT (id) DO NOTHING;
