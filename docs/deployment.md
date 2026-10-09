# Deploy to Azure

Run the provisioning script when ready to begin the cloud demo. It creates paid services, which continue billing after a partially failed deployment. Retrying with the same resource group and prefix reuses the existing resource names. Keep the cloud database password available for retries and later updates. Bicep compilation and provider validation do not prove live networking or cloud delivery; see the [verification record](verification.md) for deployment evidence.

## Prerequisites

Use an Azure subscription, a dedicated resource group, Docker, PowerShell 7, and the Azure CLI with the Container Apps extension. The first provisioning identity needs permission to create resources and role assignments (for example Contributor plus Role Based Access Control Administrator on the resource group). Select a region that supports PostgreSQL 17, Azure Managed Redis Balanced_B0, and Container Apps in your subscription. These are paid resources; check the Azure calculator before provisioning.

The script defaults to `westus3`. The implementation workspace's student subscription allows this region and reports PostgreSQL 17/B1ms availability there; `eastus` is blocked by its region policy and `eastus2` reports PostgreSQL offer restrictions. Check your own subscription before provisioning. The app runs exactly one replica to limit demo compute usage; database, cache, broker, and logging charges continue independently until resources are removed.

```powershell
az login
az account set --subscription '<subscription-id>'
az extension add --name containerapp --upgrade
az provider register --namespace Microsoft.App
az provider register --namespace Microsoft.OperationalInsights
az provider register --namespace Microsoft.DBforPostgreSQL
az provider register --namespace Microsoft.Cache
az provider register --namespace Microsoft.ServiceBus
az provider register --namespace Microsoft.ContainerRegistry
az provider register --namespace Microsoft.ManagedIdentity
```

## Configure an OIDC provider

Use an existing production OIDC provider. The local Keycloak container is for demonstration only. Configure a public SPA client using authorization code + PKCE, an API audience, and a scope granting access to the API. No client secret belongs in the browser. Register the final HTTPS URL plus `/callback` as the sign-in redirect and the origin as the logout redirect.

For Auth0, follow [Auth0 setup](auth0.md), including the browser authorization audience. Auth0 user sign-in works independently of access to Entra app registrations.

For Microsoft Entra ID, create an API registration, expose an `access_as_user` scope, set the API's requested access-token version to 2, and create a separate SPA registration that is allowed to request that scope. Use:

- Issuer: `https://login.microsoftonline.com/<tenant-id>/v2.0`
- JWKS: `https://login.microsoftonline.com/<tenant-id>/discovery/v2.0/keys`
- Audience: the API registration's application/client ID for v2 access tokens.
- Browser client ID: the SPA registration's application/client ID.
- Browser scope: `openid profile email offline_access api://<api-client-id>/access_as_user` (use your configured Application ID URI if it differs).

Verify the actual access token's `iss` and `aud` match these settings. Identity-provider admin consent may be required by your tenant. For other providers, obtain issuer/JWKS from their discovery document and configure the equivalent API audience and scope. OpsFlow uses the validated access token's `sub` as the membership key.

## Provision and publish

Set a strong unique PostgreSQL password through an environment variable. The script writes it only into temporary parameter files, passes it as a secure deployment parameter, and removes those exact files afterward. Keep the password available for later infrastructure updates.

```powershell
$env:OPSFLOW_DATABASE_PASSWORD = '<strong-unique-password>'
./scripts/deploy.ps1 `
  -ResourceGroup opsflow-demo `
  -Location westus3 `
  -Prefix opsflow `
  -OidcIssuer 'https://login.microsoftonline.com/<tenant-id>/v2.0' `
  -OidcJwks 'https://login.microsoftonline.com/<tenant-id>/discovery/v2.0/keys' `
  -OidcAudience '<api-client-id>' `
  -OidcClientId '<spa-client-id>' `
  -OidcScope 'openid profile email offline_access api://<api-client-id>/access_as_user'
```

The script provisions infrastructure, signs Docker into ACR using your Azure login, builds and pushes two versioned images, and provisions the Container App. The app identity receives ACR pull plus Service Bus sender/receiver access. Service Bus local/SAS authentication is disabled. Database and Redis credentials are stored in Container Apps secrets and connections use TLS.

The environment module explicitly selects **WorkloadProfiles** with only the **Consumption** profile, and the app selects that profile. This supports the API and web containers together. Express environments do not support this arrangement; the explicit mode uses the current preview API field supported by Azure CLI, with a documented suppression for the lagging Bicep property catalog. The environment is named `<prefix>-standard-environment` so an earlier Express environment cannot be accidentally reused.

The script prints the app URL. Register `<url>/callback` and `<url>` in the SPA's identity-provider settings, then open the URL, sign in, and create your first organization. Other users must already exist in the identity provider. Add them with the exact access-token `sub`. A user can create their own organization to see their identity-provider subject in its team screen.

Azure Managed Redis B0 and PostgreSQL B1ms are small demo tiers, not a high-availability production sizing recommendation. PostgreSQL's firewall permits Azure-origin connections, and Redis uses its authenticated public TLS endpoint. Before storing sensitive production data, implement private endpoints/VNet integration, separate runtime/migration database credentials, and an appropriate backup/availability plan. Authorization and tenant separation remain enforced in the API.

## GitHub Actions delivery

Push this repository to GitHub with `main` as the deployment branch. Verification runs for pushes and pull requests. Cloud delivery stays disabled until `AZURE_ENABLED=true` is set.

Create a GitHub `production` environment. Use a separate **user-assigned managed identity** for deployment, which can be created through Azure Resource Manager without manually registering an Entra application. Add a federated identity credential with issuer `https://token.actions.githubusercontent.com`, subject `repo:<owner>/<repo>:environment:production`, and audience `api://AzureADTokenExchange`. Give the deployment identity ACR **AcrPush** on this registry and **Container Apps Contributor** on the app/resource group. The job reads and preserves existing Container Apps secret references; allow the app's `listSecrets` operation through that role. No Azure client secret is required. The application's existing managed identity remains dedicated to image pulls and Service Bus access.

After infrastructure exists, create the deployment identity and trust (replace the repository placeholders):

```powershell
az identity create --resource-group opsflow-demo --name opsflow-deploy-identity --location westus3
az identity federated-credential create `
  --resource-group opsflow-demo `
  --identity-name opsflow-deploy-identity `
  --name github-production `
  --issuer 'https://token.actions.githubusercontent.com' `
  --subject 'repo:<owner>/<repo>:environment:production' `
  --audiences 'api://AzureADTokenExchange'
```

Use its `clientId` for `AZURE_CLIENT_ID` and its `principalId` for the two Azure role assignments. Resource creation, federation, and role assignment must be permitted by your subscription/directory policies; this approach does not bypass those controls. An existing authorized Entra application with equivalent federation can also be used.

Configure repository/environment variables:

Set `AZURE_ENABLED` as a **repository** variable so the verification job can read it before entering the production environment. The remaining deployment values can be repository or production-environment variables.

| Variable                | Value                                       |
| ----------------------- | ------------------------------------------- |
| `AZURE_ENABLED`         | `true`                                      |
| `AZURE_CLIENT_ID`       | Deployment managed identity client ID       |
| `AZURE_TENANT_ID`       | Entra tenant ID                             |
| `AZURE_SUBSCRIPTION_ID` | Subscription ID                             |
| `AZURE_RESOURCE_GROUP`  | Resource group used above                   |
| `AZURE_APP_NAME`        | Bicep output appName, usually `opsflow-app` |
| `ACR_NAME`              | Bicep output registryName                   |
| `OIDC_ISSUER`           | Same issuer used by the API                 |
| `OIDC_CLIENT_ID`        | Browser SPA client ID                       |
| `OIDC_SCOPE`            | Full OIDC + API scope string                |
| `OIDC_AUTHORIZATION_AUDIENCE` | Auth0 API identifier; leave empty for the Entra example |

Find infrastructure outputs with `az deployment group show -g opsflow-demo -n opsflow-infra --query properties.outputs`. Deployments use immutable commit SHA image tags and update both containers together. The pipeline does not re-provision infrastructure or rotate database credentials. Change infrastructure using Bicep/the provisioning script, and rerun it when changing API identity-provider settings.

## Acceptance and proof

Complete these before claiming the project is deployed:

1. Open the public URL and verify `/api/health` returns `UP`.
2. Sign in, create two organizations with distinct users, and verify foreign-organization REST requests return 404.
3. Add a responder/viewer and verify the relevant role restrictions; acknowledge and resolve an incident.
4. Verify an outbox event becomes published and all current organization members receive one notification.
5. In Service Bus Explorer send an invalid JSON message to `incident-events`. Verify it appears in the DLQ with reason `InvalidEvent` and no notification is created.
6. Temporarily make PostgreSQL unavailable during consumption. Verify redelivery and eventual success or `MaxDeliveryCountExceeded` after five broker delivery attempts. Restore connectivity, then replay the failed message through Explorer.
7. Replay a valid event body with a new broker message ID (to bypass broker duplicate suppression). Verify the original body event ID creates no duplicate notifications.
8. Trigger the GitHub deployment workflow and record the successful run, app URL, revision, image SHA, and notification evidence.

The broker uses duplicate detection for ten minutes. When repairing/replaying DLQ messages, use a new **broker** message ID if replaying within that window, while retaining the **body** event ID. A previously processed event remains idempotent. Published outbox events are only proof of transport acceptance; verify the inbox or processed-event record to prove consumer completion.

Use a dedicated resource group so cleanup is straightforward. When finished, remove that dedicated group through the Azure portal after reviewing the resources it contains. This repository does not automatically delete cloud data.

References: [Entra token validation and audiences](https://learn.microsoft.com/en-us/entra/identity-platform/access-tokens), [GitHub OIDC with Azure](https://learn.microsoft.com/en-us/azure/developer/github/connect-from-azure-openid-connect), [managed identity federation](https://learn.microsoft.com/en-us/entra/workload-id/workload-identity-federation-create-trust-user-assigned-managed-identity), [Container Apps image pull with managed identity](https://learn.microsoft.com/en-us/azure/container-apps/managed-identity-image-pull), [Service Bus DLQ](https://learn.microsoft.com/en-us/azure/service-bus-messaging/service-bus-dead-letter-queues).
