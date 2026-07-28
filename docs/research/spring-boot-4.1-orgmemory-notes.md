# Spring Boot 4.1 Notes For OrgMemory

Reviewed against the
[Spring Boot 4.1 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes)
and the
[HTTP client reference](https://docs.spring.io/spring-boot/4.1/reference/io/rest-client.html)
on 2026-07-29, then verified against the resolved `4.1.0` JARs.

## Adopted Now

- `HttpClientSettings` gives the model probe one immutable bundle for connect
  timeout, read timeout, redirect policy, and address filtering.
- `HttpRedirects.DONT_FOLLOW` prevents provider discovery from following an
  attacker-controlled redirect.
- `InetAddressFilter.externalAddresses()` protects fixed public-provider
  probes; operator-allowlisted private gateways use an explicit origin policy.
- The JDK `ClientHttpRequestFactoryBuilder` keeps the probe adapter small and
  avoids adding another HTTP implementation.
- Dynamic Spring AI models receive Micrometer observation and meter registries.

## Useful Next, Not Added To This Increment

- HTTP Service interfaces and named service-client groups can consolidate
  stable connector APIs that share base URL, headers, timeouts, redirects, and
  SSL bundles. They are a better fit for fixed connector contracts than for
  runtime-created AI profiles.
- Global `spring.http.clients.*` settings are useful as a conservative baseline
  for all auto-configured clients, but provider-specific probe limits must
  remain stricter.
- SSL bundles should be used when production 9Router/LiteLLM endpoints require
  private certificate authorities or mTLS.
- Automatic `@Async` observation-context propagation is relevant when connector
  work moves onto annotated async methods; the current virtual-thread pipelines
  keep their explicit telemetry.
- `management.opentelemetry.enabled` and the new sampler/limit properties can
  simplify an operations-level kill switch and cardinality controls.

Spring Boot's address filter applies to Boot-managed HTTP clients. It does not
automatically wrap Spring AI's internal OpenAI/Anthropic SDK clients, so
deployment origin allowlists and network egress policy remain mandatory.
