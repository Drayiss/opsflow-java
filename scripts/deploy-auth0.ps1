[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$ResourceGroup = 'opsflow-demo',
    [string]$Location = 'westus3',
    [string]$Prefix = 'opsflow',
    [string]$EnvironmentFile = (Join-Path (Split-Path $PSScriptRoot -Parent) '.env.auth0.local')
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $EnvironmentFile)) {
    throw 'Prepare .env.auth0.local from .env.auth0.example before deploying.'
}
$taskProfile = @{}
foreach ($taskLine in Get-Content -LiteralPath $EnvironmentFile) {
    $taskLine = $taskLine.Trim()
    if (-not $taskLine -or $taskLine.StartsWith('#')) { continue }
    $taskSeparator = $taskLine.IndexOf('=')
    if ($taskSeparator -gt 0) {
        $taskProfile[$taskLine.Substring(0, $taskSeparator)] = $taskLine.Substring($taskSeparator + 1)
    }
}
foreach ($taskKey in @('OIDC_ISSUER','OIDC_JWKS','OIDC_AUDIENCE','VITE_OIDC_CLIENT_ID','VITE_OIDC_SCOPE','VITE_OIDC_AUTHORIZATION_AUDIENCE')) {
    if (-not $taskProfile[$taskKey] -or $taskProfile[$taskKey] -match 'YOUR-') { throw "Configure $taskKey in $EnvironmentFile first." }
}
if (-not $PSCmdlet.ShouldProcess("$ResourceGroup in $Location", 'Provision paid Azure services and deploy OpsFlow with Auth0')) { return }

# Local demonstration DATABASE_PASSWORD is deliberately not used for Azure.
$taskPreviousPassword = $env:OPSFLOW_DATABASE_PASSWORD
try {
    if (-not $env:OPSFLOW_DATABASE_PASSWORD) {
        $taskSecurePassword = Read-Host 'Enter a strong cloud database password (save it in your password manager)' -AsSecureString
        $env:OPSFLOW_DATABASE_PASSWORD = [Net.NetworkCredential]::new('', $taskSecurePassword).Password
    }
    $taskParameters = @{
        ResourceGroup = $ResourceGroup
        Location = $Location
        Prefix = $Prefix
        OidcIssuer = $taskProfile['OIDC_ISSUER']
        OidcJwks = $taskProfile['OIDC_JWKS']
        OidcAudience = $taskProfile['OIDC_AUDIENCE']
        OidcClientId = $taskProfile['VITE_OIDC_CLIENT_ID']
        OidcScope = $taskProfile['VITE_OIDC_SCOPE']
        OidcAuthorizationAudience = $taskProfile['VITE_OIDC_AUTHORIZATION_AUDIENCE']
    }
    & (Join-Path $PSScriptRoot 'deploy.ps1') @taskParameters
} finally {
    $env:OPSFLOW_DATABASE_PASSWORD = $taskPreviousPassword
    if ($taskSecurePassword) { $taskSecurePassword.Dispose() }
}
