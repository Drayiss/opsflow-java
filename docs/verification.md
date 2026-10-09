# Verification record

Verified in the implementation workspace on October 8, 2026:

| Check | Result |
|---|---|
| Maven verification on Java 21 | 14 tests passed, zero failures/errors/skips; runnable JAR packaged |
| PostgreSQL/Redis integration | Real PostgreSQL 17 and Redis 7.4 through Testcontainers |
| JWT validation | Trusted signed tokens accepted; incorrect signature, issuer, audience, and expired tokens rejected |
| Tenant/RBAC checks | Foreign reads/mutations denied; responder/viewer restrictions enforced; final admin protected |
| Incident workflows | Transitions, invalid assignments, optimistic locking, and transactional event creation passed |
| Consumer reliability | Concurrent duplicates, transaction rollback/retry, outbox backoff/dead state, and replay authorization passed |
| Azure message callback | Completion order, transient abandon, and malformed-message dead-letter decisions passed using SDK context mocks |
| Redis behavior | Tenant-specific cache, eviction after committed writes, and unavailable-Redis fallback passed |
| Frontend build | TypeScript compilation and Vite production bundle passed |
| Browser verification | 3 tests passed against Dockerized frontend/API with real Keycloak sign-in |
| Containers | All five Compose services started; public web readiness endpoint returned UP |
| Azure templates | Both Bicep files compiled without diagnostics |
| Performance | Six k6 runs at 50 VUs; exact results in `perf/results/measured.json` and `docs/performance.md` |
| Demo seeding | Repeated seed with 200,000 benchmark rows present left only the seven demo activities/events; benchmark records remained intact |

Browser coverage includes create/acknowledge/assign/comment/resolve, activity, inbox notifications, separate tenant screens, and responder membership-control restrictions. All local identities and incidents are synthetic.

**Live Azure provisioning, identity-provider setup, Service Bus delivery/DLQ behavior against a real namespace, and GitHub workflow execution are pending the user-run deployment.** Template compilation and SDK callback mocks are not proof of a live cloud deployment. Follow `docs/deployment.md` and save the resulting public URL, revision SHA, successful workflow run, and notification/DLQ evidence.

## Auth0 configuration checks — October 9, 2026

Added an optional browser authorization audience, configurable Compose issuer/audience/JWKS, an ignored local Auth0 profile, and matching Docker/deployment build arguments. The original Keycloak defaults remain available.

- TypeScript and Vite production build passed, including the Docker web image build.
- All three existing browser tests passed after rebuilding the local Compose app with Keycloak.
- An isolated browser check used the configured Auth0 tenant's live discovery metadata and intercepted the authorization navigation. It verified the SPA client ID, API audience, requested OIDC/API scopes, authorization code flow, S256 PKCE challenge, state, and callback path. It also checked the Compose API issuer/audience/JWKS values and that Auth0 screens omit Keycloak demo credentials.
- The deployment PowerShell script parsed without errors, and the prepared local Auth0 environment file is ignored by Git.

After the user confirmed the dashboard settings were saved, the Auth0 profile was built and started locally. The public local health endpoint returned `UP`, the running API had the expected Auth0 issuer/JWKS/audience, and the sign-in button reached the real Auth0 **OpsFlow Web** login page with signup available.

No Auth0 user signed in and no Auth0 token was exchanged during these checks. Actual Auth0 sign-in, API token acceptance, refresh rotation, and sign-out still require the local acceptance steps in `docs/auth0.md`. The running local app now uses Auth0. No Azure resources were provisioned.

The user subsequently reported that Auth0 sign-in worked. Refresh rotation, sign-out, and the full Auth0 tenant/role acceptance flow have not been independently verified.

## Azure preparation — October 9, 2026

- Confirmed the student subscription is enabled and its allowed-region policy excludes `eastus`.
- PostgreSQL capability checks reported offer restrictions in `eastus2` and `westus2`; `westus3`, `canadacentral`, and `mexicocentral` returned supported editions and versions. Selected `westus3`, including PostgreSQL 17/B1ms availability and unused Container Apps environment quota.
- Registered the missing `Microsoft.ManagedIdentity` provider; all seven required providers are registered.
- Compiled both Bicep templates after limiting the demo app to exactly one replica, and checked deployment PowerShell syntax.
- Created the `opsflow-demo` resource group in `westus3`. Azure provider-level validation of the infrastructure template succeeded without deploying its resources.
- Created `opsflow-deploy-identity`, configured federation for `repo:Drayiss/opsflow-java:environment:production`, and assigned Container Apps Contributor on the dedicated resource group. ACR push permission is pending registry creation.

Only the resource group and deployment identity have been created. Paid services, actual deployment, Azure Service Bus acceptance, and GitHub delivery remain pending. Provider validation is not proof of successful resource creation or live notification delivery.

## GitHub verification — October 9, 2026

Uploaded the initial implementation to `Drayiss/opsflow-java`. The [Verify workflow](https://github.com/Drayiss/opsflow-java/actions/runs/37885324583) passed for commit `dabeb30e3e933995b7508346f6ab171841a2240b`, including backend tests, frontend compilation, Docker builds, demo seeding, and three real Keycloak browser checks on the hosted Ubuntu runner. The Azure deployment workflow was skipped because cloud delivery is not enabled.

Added an Auth0 deployment wrapper that reads the prepared public OIDC profile and prompts locally for a cloud database password. Its syntax, dry run, parameter forwarding, and password-environment restoration on success/failure were checked without contacting Azure. This does not constitute a live service deployment.

## Initial live infrastructure deployment and role repair — October 9, 2026

The user ran the deployment command. PostgreSQL, Redis, Service Bus and its queue, Container Registry, Log Analytics, the Container Apps environment, and the app managed identity were created successfully. The infrastructure deployment failed only on the receiver role assignment: its template contained an incorrect built-in role ID. These existing services can incur charges even though the deployment was marked failed.

Verified the correct Azure Service Bus Data Receiver role ID against the live subscription and Microsoft's role reference: `4f6d3b9b-027b-4f4c-9142-0e5a2a2247e0`. Corrected the template and created the previously failed assignment with the same assignment name. Azure lists both Sender and Receiver roles on the app identity. Recompiled the template. App image publication and Container App deployment remain pending a retry with the user's cloud database password.
