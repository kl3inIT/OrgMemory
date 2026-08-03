# Verification - RAG Workload Routing And Luna Evaluation

Verified on 2026-08-02.

- Feature PR #272 merged as `24c31aea`; production binding repair PR #275
  merged as `2891e7b0` after CI and review gates passed.
- Main CI, production image assembly, documentation delivery, and production
  deployment passed for deployed product SHA `6b36e128`.
- ZM API, worker, web, MCP, Keycloak, OpenFGA, Flyway V20, and the observability
  stack were healthy after deployment.
- API and worker environments resolved Answer `gpt-5.6-sol`, Keyword
  `gpt-5.6-luna` with reasoning `none`, and Graph `gpt-5.4-mini`.
- The bounded evaluation passed Keyword Luna at 12/12 valid with equal 0.875
  recall and lower p95 latency; Graph Luna failed at 11/12 valid and higher p95,
  so Graph retained `gpt-5.4-mini`.
- Existing production graph canaries completed on `gpt-5.4-mini`; automated
  coverage proves new schema-v2 profile identity and schema-v1 restoration.
- No reindex was started. There were no processing jobs left in the ingestion
  queue; three exhausted validation failures predated this release.
- A fresh browser upload/query/delete canary was not run because the isolated
  browser had no authorized production session. No production identity or
  credential was created to bypass that boundary.
