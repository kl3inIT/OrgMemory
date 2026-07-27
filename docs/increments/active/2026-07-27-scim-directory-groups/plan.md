# Plan

## PR G1 — Directory Group Ledger, CRUD, And Search

Scope:

- add Group resource and membership persistence with composite tenant keys;
- implement Group schema mapping, POST, GET, list, `.search`, PUT metadata, and
  tombstone DELETE without membership mutation yet;
- implement `displayName eq`, `externalId eq`, pagination, attributes, and
  excluded attributes;
- keep the Group ResourceType unadvertised and the partial routes unavailable
  to every real connection;
- add SCIM error, uniqueness, and tombstone behavior.

Merge gate:

- [ ] duplicate display name and external ID races leave one resource;
- [ ] all group lookups are connection- and organization-scoped;
- [ ] empty queries and stable pagination match the User profile;
- [ ] deleting a group preserves audit and returns later SCIM `404`;
- [ ] merely enabling Groups changes no OpenFGA tuple or source ACL row;
- [ ] every connection continues to advertise no Group ResourceType.

## PR G2 — Atomic Direct Membership PUT/PATCH

Scope:

- implement direct User membership add, replace, and remove;
- support pathless PATCH and `members[value eq "<id>"]`;
- support full PUT membership replacement;
- make PATCH operation names case-insensitive;
- enforce body, operation, and membership-count limits;
- reject nested groups, unknown/tombstoned users, duplicates, cross-connection
  members, and cross-organization members;
- serialize or compare-and-set concurrent replacement and delta updates.
- advertise/enable the complete Group ResourceType only after this PR's
  membership profile and tests pass.

Merge gate:

- [ ] Entra and Okta membership fixture corpora pass;
- [ ] a mixed valid/invalid member request commits nothing;
- [ ] concurrent replace/add/remove has a deterministic final version;
- [ ] `excludedAttributes=members` omits the collection correctly;
- [ ] large-group bounds return a safe SCIM error rather than exhausting
  memory;
- [ ] directory membership changes no application authorization;
- [ ] Source Group sealed generations remain byte/row unchanged.
- [ ] Groups discovery becomes visible only for an explicitly enabled
  connection after all G2 gates pass.

## PR G3 — Directory Group Administration And Live Proof

Scope:

- add Directory Groups list/detail and direct member views;
- show immutable ID, connection, lifecycle, member count, last sync, and
  sanitized failures;
- state explicitly that the group grants no access;
- add browser tests and the provider-backed Group lifecycle smoke;
- capture drift/conflict diagnostics without raw member profile values.

Live proof:

1. create a directory group;
2. add two provisioned users;
3. remove one member and replace the member set;
4. rename the group and prove identity is unchanged;
5. query with and without members;
6. attempt a cross-tenant and nested member and prove atomic denial;
7. prove both users' effective permissions are unchanged;
8. delete and prove tombstone behavior;
9. disable only Groups and verify Users remain functional.

Increment exit:

- [ ] G1, G2, and G3 are merged in order.
- [ ] Group vendor fixtures and live proof pass.
- [ ] No OpenFGA, app-role, department, or Source ACL mutation exists.
- [ ] Authorization mapping remains a separate disabled feature.
