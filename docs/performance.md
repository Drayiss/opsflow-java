# Performance evidence

Measured October 8, 2026 on local Docker Desktop, using **50 simulated concurrent users** and **200,000 incidents**. Median p95 dashboard API latency fell from **239.96 ms to 2.29 ms**, a **99.05% reduction**. All requests succeeded in the final six runs.

These measurements used Java 21 before the project's Java 25 migration. They remain evidence for that recorded build; the benchmark has not yet been repeated on Java 25.

| Run | Redis disabled p95 | Redis enabled p95 |
|---|---|---|
| 1 | 249.80 ms | 2.50 ms |
| 2 | 239.96 ms | 2.29 ms |
| 3 | 231.07 ms | 2.27 ms |
| Median | **239.96 ms** | **2.29 ms** |

Source: [`measured.json`](../perf/results/measured.json). The reproducible benchmark is `perf/benchmark.ps1`; it writes a new report only after successful runs. Each measured phase lasts 30 seconds, with equal warm-up beforehand. The final run used the Compose network directly and required zero HTTP failures and 100% response checks. Docker had 16 logical CPUs and 15.58 GiB of memory available. Image/metric details are recorded in the JSON report. An initial host-port-forwarding run had connection timeouts and is excluded from this result.

The workload is the authenticated organization summary endpoint over 200,000 synthetic incidents with tenant/status indexes. Baseline and optimized runs have the same database schema, membership checks, concurrency, and warm-up. Only Redis summary caching changes. Every response is checked for HTTP success and the expected incident count. Three runs per mode are compared by median p95 latency.

All virtual users read the same organization's summary using one demo identity, creating a hot-cache dashboard workload. The latency metric excludes authentication and warm-up; request totals in the JSON include those setup requests. PostgreSQL indexes are present in both modes, so this comparison isolates Redis caching. A separate query plan confirms incident pagination uses `incidents_tenant_status_idx`: [`index-query-plan.txt`](index-query-plan.txt).

The results describe local Docker Desktop and a read-only dashboard workload. They do not establish a whole-API improvement, a mixed write workload, the individual contribution of indexing, or Azure performance. Measure those separately before broadening the claim. A 15-second summary TTL permits bounded staleness and is part of the optimization tradeoff.

## Accurate resume wording today

**OpsFlow — Multi-Tenant Incident Management Platform**

*Java, Spring Boot, Spring Security, React, TypeScript, PostgreSQL, Redis, Azure Service Bus, Docker, Azure*

- Engineered a multi-tenant incident management platform using Spring Boot, React, and PostgreSQL, exposing RESTful APIs for incident tracking, organization management, and role-based workflows.
- Secured REST endpoints with Spring Security, OAuth 2.0/OIDC, and JWT validation, implementing role-based access control and tenant-level data isolation across organizations.
- Designed an event-driven notification pipeline using Azure Service Bus and asynchronous Spring consumers, implementing transactional outbox delivery, idempotent processing, retry policies, and dead-letter queues.
- Indexed tenant-scoped PostgreSQL queries and implemented Redis caching, reducing **p95 dashboard API latency by 99.05% under 50 simulated concurrent users** in a local load test; containerized services and built Azure infrastructure and GitHub Actions CI/CD workflows.

After completing the Azure acceptance checks, add “and deployed” to the first bullet and replace “built Azure infrastructure and GitHub Actions CI/CD workflows” with “deployed services to Azure using GitHub Actions CI/CD.” Keep the performance claim scoped to the dashboard benchmark unless you also measure the production workload.
