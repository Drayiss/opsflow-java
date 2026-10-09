# Auth0 sign-in

Auth0 authenticates users; OpsFlow stores organizations and their administrator, responder, and viewer memberships in PostgreSQL. Auth0 Organizations and Auth0 role assignments are not required. The existing `oidc-client-ts` browser client uses authorization code + PKCE; no Auth0 client secret is needed.

## Dashboard configuration

1. Create a **Single Page Application** named **OpsFlow Web**. Keep token endpoint authentication set to **None**.
2. Under its **Settings**, save:
   - Allowed Callback URLs: `http://localhost:5174/callback`
   - Allowed Logout URLs: `http://localhost:5174`
   - Allowed Web Origins: `http://localhost:5174`
3. Create a custom API named **OpsFlow API** with identifier `https://opsflow-api` and **RS256** signing. This identifier is an audience, not a hosted website.
4. Under API **Permissions**, add `access_as_user`. Under **Application Access**, grant **OpsFlow Web** **User-Delegated Access** to that permission. Client Access and Anonymous Access are unnecessary for browser sign-in.
5. Under API **Settings**, enable **Allow Offline Access** and save.
6. Return to **Applications → Applications → OpsFlow Web → Settings**. Under **Refresh Token Rotation**, enable **Allow Refresh Token Rotation**, set the overlap period to **3 seconds**, and save. Keep refresh tokens expiring; the default maximum lifetime is 30 days. In **Advanced Settings → Grant Types**, ensure **Authorization Code** and **Refresh Token** are enabled.
7. Under the application's **Connections**, ensure a database or social connection is enabled so a test user can sign in. For database signup, the connection must allow signups.

Use the URLs above, including port **5174**, even when the Auth0 quickstart suggests Vite's default port 5173. Do not replace the project's login components with the quickstart sample.

## Run with Auth0 locally

Copy `.env.auth0.example` to `.env.auth0.local`, and replace `YOUR-TENANT` and `YOUR-SPA-CLIENT-ID` with your domain and public application client ID. The issuer's trailing slash matters. The workspace may already contain a prepared `.env.auth0.local`; check it before copying over it. This file is ignored by Git.

From the repository root:

```powershell
docker compose --env-file .env.auth0.local up -d --build api web
```

The frontend's OIDC values are compiled into its image, so changing the environment file requires rebuilding `web`. The backend validates tokens against the configured issuer, JWKS, and API audience. Keycloak can remain running for the original demo; the Auth0 profile directs sign-in and JWT validation to Auth0.

Open `http://localhost:5174` in a fresh private browser window. Click **Sign in to your workspace**, then **Sign up** on Auth0's login page, or use an existing user from an enabled connection. Your Auth0 dashboard account does not automatically create an application user. The local `alice`, `bob`, and `eve` credentials belong to Keycloak.

After returning to OpsFlow, create an organization. Auth0 users have different subjects from the demo users and do not inherit their memberships. Create an incident and verify it appears in the workspace and inbox. Test sign-out and sign-in again. Use a second Auth0 user to check organization separation and membership roles. Never paste access tokens, refresh tokens, or passwords into project documentation.

To return to the original demo:

```powershell
docker compose up -d --build api web
```

This uses `.env` and Compose defaults. If `.env` was changed to Auth0, restore its Keycloak settings from `.env.example` first. PostgreSQL data is preserved; each provider's users still need their own organization memberships. Existing browser tests and the benchmark use Keycloak, so restore the demo profile before running them.

## Azure configuration

Use the same issuer, JWKS, API audience, SPA client ID, and scopes with `scripts/deploy.ps1`. For Auth0, also pass `-OidcAuthorizationAudience 'https://opsflow-api'` and `-OidcScope 'openid profile email offline_access access_as_user'`. Before provisioning, follow the region, permission, and cost checks in [the deployment guide](deployment.md).

After provisioning, add the final HTTPS app origin and its `/callback` URL to Auth0's allowed URLs. Keep localhost entries if you want to continue testing locally. For GitHub Actions, set `OIDC_AUTHORIZATION_AUDIENCE=https://opsflow-api` and the full `OIDC_SCOPE` above. These are public configuration values; Azure deployment authentication is configured separately from Auth0 user sign-in.

## Troubleshooting

- **Callback URL mismatch:** Check the exact scheme, port, and `/callback` suffix.
- **Client not authorized for the API:** Check User-Delegated Access for **OpsFlow Web** and `access_as_user`.
- **API returns 401:** Check issuer including its trailing slash, API audience, JWKS URL, and that the rebuilt web image requests `audience=https://opsflow-api`. An ID token cannot replace an API access token.
- **Refresh token missing or renewal fails:** Check API Allow Offline Access, the `offline_access` scope, Refresh Token grant type, and rotation settings. Sign in again after changing them.
- **Empty organizations:** Create your first organization with this Auth0 user. The demo memberships are for Keycloak subjects.

References: [Auth0 API settings](https://auth0.com/docs/get-started/apis/api-settings), [API access policies](https://auth0.com/docs/get-started/apis/api-access-policies-for-applications), [authorization code with PKCE](https://auth0.com/docs/api/authentication/authorization-code-flow-with-pkce/authorize-with-pkce), [refresh-token rotation](https://auth0.com/docs/secure/tokens/refresh-tokens/configure-refresh-token-rotation).
