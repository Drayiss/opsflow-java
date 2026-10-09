import { UserManager, WebStorageStateStore } from "oidc-client-ts";
const authorizationAudience = import.meta.env.VITE_OIDC_AUTHORIZATION_AUDIENCE;
export const auth = new UserManager({
  authority:
    import.meta.env.VITE_OIDC_AUTHORITY ||
    "http://localhost:8180/realms/opsflow",
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID || "opsflow-web",
  redirect_uri: `${location.origin}/callback`,
  post_logout_redirect_uri: location.origin,
  response_type: "code",
  scope: import.meta.env.VITE_OIDC_SCOPE || "openid profile email",
  extraQueryParams: authorizationAudience
    ? { audience: authorizationAudience }
    : undefined,
  userStore: new WebStorageStateStore({ store: sessionStorage }),
  automaticSilentRenew: true,
  monitorSession: false,
});
