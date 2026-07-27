# SCIM Protocol Profile

This profile freezes the first OrgMemory SCIM contract. It is implementation
intent until the User and Group increments expose their complete resource
types. Foundation discovery must not advertise either resource type.

## User

| Attribute | Requirement | Mutability | OrgMemory rule |
| --- | --- | --- | --- |
| `schemas` | required | immutable | Core User plus allowlisted Enterprise User extension only |
| `id` | server response | read-only | Stable SCIM resource UUID, distinct from `app_users.id` |
| `externalId` | required | immutable | Connection-scoped workforce correlation key in the first profile |
| `userName` | required | read-write | Connection-scoped, case-insensitive normalized uniqueness |
| `name.givenName` | optional | read-write | Profile data only |
| `name.familyName` | optional | read-write | Profile data only |
| `name.formatted` | optional | read-write | Profile data only |
| `displayName` | optional | read-write | Profile data only |
| `title` | optional | read-write | Profile data only |
| `active` | optional, default true | read-write | Changes directory lifecycle only; local suspension still wins |
| `emails` | required | read-write | Exactly one canonical primary work email |
| `groups` | response only | read-only | Hidden until the complete Group profile ships |
| `meta` | server response | read-only | No public ETag in the first profile |

The Enterprise User extension allows `employeeNumber`, `department`,
`division`, `organization`, and `costCenter`. `manager` remains unsupported
until its lifecycle semantics are designed. These values are directory profile
data: `department` does not select an OrgMemory Department.

`password` is unsupported input. Any occurrence returns `400` with
`scimType=invalidValue`; it is never silently discarded, logged, or persisted.
Provider setup must disable password mapping because Keycloak and the upstream
IdP own authentication.

`roles`, `entitlements`, arbitrary extensions, OrgMemory role, organization,
department ID, Knowledge Space, and Source Group are rejected. SCIM profile
attributes never become authorization instructions.

## Group

| Attribute | Requirement | Mutability | OrgMemory rule |
| --- | --- | --- | --- |
| `schemas` | required | immutable | Core Group only |
| `id` | server response | read-only | Stable SCIM Group UUID |
| `externalId` | required | immutable | Connection-scoped directory group ID |
| `displayName` | required | read-write | Display only; never an authorization instruction |
| `members` | optional | read-write by PATCH | Direct User membership only |
| `members.value` | required per member | immutable reference | OrgMemory SCIM User ID |
| `members.display` | response hint | read-only | Non-authoritative |
| `members.$ref` | response hint | read-only | Server-generated when emitted |
| `meta` | server response | read-only | Internal version is not advertised as ETag |

Nested Groups, dynamic membership, group roles, and membership supplied through
Group `PUT` are unsupported in the first Group profile.

## Protocol Surface

- Supported after the relevant implementation ships: RFC filter AST, one-based
  pagination, `.search`, `attributes`, `excludedAttributes`, and atomic PATCH
  with case-insensitive operation names, pathless objects, and allowlisted path
  forms.
- Explicitly unsupported: Bulk, sort, ETag/preconditions, cursor pagination,
  password change, nested Groups, arbitrary schemas, and cross-resource
  transactions.
- Filter input is bounded to 2,048 characters, AST depth 8, and 64 AST nodes.
  PATCH is separately bounded by body size and operation count in F3.
- Unsupported grammar returns `invalidFilter`; unsupported values return
  `invalidValue`; read-only/immutable writes return `mutability`.

The fixture corpus under
`components/scim-protocol-conformance/src/test/resources/scim` is the
executable source for provider dialect and error examples.

## Contract Drift

SCIM owns `contracts/scim-openapi.json`, generated independently from the
product `/api/**` contract. The F3 build generates to a temporary file and
compares it byte-for-byte after stable ordering against the committed snapshot.
The browser client generator must continue consuming only
`contracts/openapi.json`. A capability is added to discovery and contract in the
same PR as its complete tests; incomplete User or Group resource types remain
absent rather than partially advertised.
