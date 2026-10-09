# Operations

## Health and logs

- `/actuator/health/liveness` checks the API process. Readiness also checks PostgreSQL. Redis is deliberately excluded from readiness because database fallback remains functional.
- `docker compose logs --tail 100 api` shows migration, authentication-provider, outbox, and processor failures. Logging avoids tokens and event bodies.
- The administrator Deliveries screen shows pending, published, and failed outbox records. Published means accepted by the selected transport, not proof of every recipient's consumption.
- Azure Container Apps sends logs to Log Analytics. Monitor Service Bus `DeadletteredMessages`, backlog, database connection use, and CPU/memory. A broker DLQ is separate from failed outbox publication records.

## Failed notifications

Local/outbox failures retry up to eight times with exponentially increasing delay, capped at 300 seconds. A dead outbox event waits for administrator retry. Fix the underlying problem before retrying. Use the Deliveries screen or `POST /api/organizations/{org}/deliveries/{id}/retry`.

In Azure, the SDK retries transport operations three times. Transient consumer failures abandon messages; the queue moves them to its DLQ after the configured five delivery attempts. Invalid payloads are immediately dead-lettered. Inspect and repair these in Service Bus Explorer. Keep the payload's event ID, but use a fresh broker message ID when resubmitting inside the broker's duplicate detection window. The database prevents repeated notification creation even when the broker deduplication window expires.

Events are at least once, not exactly once delivery. The recipient-record effect is idempotent. Do not remove processed-event rows unless the corresponding history is being retired and replay implications are understood. Outbox, activity, notification, and processed-event tables have no automatic retention policy in this small implementation; establish one before long-running production use.

## Common local issues

| Symptom                           | Action                                                                                                                                                  |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Empty workspace                   | Run `scripts/seed-demo.ps1`, or create an organization after signing in.                                                                                |
| Login redirect fails              | Use `http://localhost:5174`, matching the imported Keycloak client's redirect/origin.                                                                   |
| 401 with a token                  | Verify issuer, audience, expiry, and that this is an access token for `opsflow-api`.                                                                    |
| User cannot see an organization   | Verify its membership uses the access token's exact `sub`, not an email or username.                                                                    |
| Assignment rejected               | The assignee must be an administrator/responder in the same organization.                                                                               |
| 409 on update                     | Reload the incident and submit its current version; check the allowed status transition.                                                                |
| Counts briefly lag                | Summary cache TTL is 15 seconds. Incident detail remains authoritative.                                                                                 |
| Keycloak realm changes ignored    | Import only creates a realm once. Edit the local realm using Keycloak's admin console or intentionally reset its demo volume after reviewing data loss. |
| API/database credentials disagree | Changing POSTGRES_PASSWORD does not change the password in an existing PostgreSQL volume. Change the database role password explicitly.                 |
| Port in use                       | Stop the source-dev API/Vite processes before starting the corresponding Compose services.                                                              |

Stop local services with `docker compose down`, preserving volumes. Avoid `down -v` unless you intend to discard all local database and identity-provider data.

## Database changes and rollback

Flyway applies versioned migrations automatically; never edit a migration already deployed. Add a new migration for schema changes. Before releasing a destructive migration, back up PostgreSQL and design compatibility with the prior image. Restore a prior Container Apps revision/image if an application release fails; schema rollbacks require an explicit forward fix or database restore. The supplied schema is intentionally small and migrations are additive at initial release.

## Configuration

`DATABASE_POOL_SIZE` defaults to 20 locally; the Azure template sets 10 connections per replica.

| Variable                                                        | Default / purpose                                                                                |
| --------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`            | PostgreSQL connection                                                                            |
| `OIDC_ISSUER`, `OIDC_JWKS`, `OIDC_AUDIENCE`                     | JWT validation; defaults to local Keycloak/opsflow-api                                           |
| `CORS_ORIGIN`                                                   | Exact allowed frontend origin                                                                    |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL`       | Redis connection                                                                                 |
| `CACHE_ENABLED`                                                 | `true`; disables only dashboard caching when false                                               |
| `NOTIFICATION_TRANSPORT`                                        | `local` or `azure`                                                                               |
| `SERVICEBUS_NAMESPACE`                                          | Fully qualified namespace for managed identity                                                   |
| `SERVICEBUS_CONNECTION_STRING`                                  | Optional local emulator/development connection string; never needed in supplied Azure deployment |
| `SERVICEBUS_QUEUE`                                              | `incident-events`                                                                                |
| `DISPATCH_ENABLED`                                              | `true`; disable during isolated tests                                                            |
| `AZURE_CLIENT_ID`                                               | User-assigned managed identity client ID in Azure                                                |
| `VITE_OIDC_AUTHORITY`, `VITE_OIDC_CLIENT_ID`, `VITE_OIDC_SCOPE` | Public build-time SPA configuration                                                              |
| `VITE_API_URL`                                                  | `/api`; public build-time API prefix                                                             |
| `API_UPSTREAM`                                                  | Web runtime proxy; `api:8080` locally, `localhost:8080` in Azure                                 |

Only `local` and `azure` are supported transports. Do not use the local fallback to assess Azure broker retry behavior. Frontend VITE variables are embedded during build; changing them requires a new web image. They must contain no secrets.
