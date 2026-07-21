# Relational Capability Graph Coverage

Current graph authorization is exercised indirectly by controller/service build
and broader integration tests, but there is no dedicated graph response test.

Gaps: exact node/edge contract, permission-negative metadata leak test, and real
browser rendering test. The future semantic graph requires a separate conformance
testkit and must not reuse this visualization as correctness evidence.
