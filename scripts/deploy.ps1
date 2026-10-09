param(
    [Parameter(Mandatory=$true)][string]$ResourceGroup,
    [string]$Location = 'westus3',
    [string]$Prefix = 'opsflow',
    [Parameter(Mandatory=$true)][string]$OidcIssuer,
    [Parameter(Mandatory=$true)][string]$OidcJwks,
    [Parameter(Mandatory=$true)][string]$OidcAudience,
    [Parameter(Mandatory=$true)][string]$OidcClientId,
    [string]$OidcScope = 'openid profile email offline_access',
    [string]$OidcAuthorizationAudience = '',
    [string]$ImageTag = (Get-Date -Format 'yyyyMMddHHmmss')
)
$ErrorActionPreference = 'Stop'
if (-not $env:OPSFLOW_DATABASE_PASSWORD) { throw 'Set OPSFLOW_DATABASE_PASSWORD to a strong database password first.' }
function Invoke-Checked { param([string]$Command, [string[]]$Arguments); & $Command @Arguments; if ($LASTEXITCODE -ne 0) { throw "$Command failed" } }
$taskRoot = Split-Path $PSScriptRoot -Parent
Push-Location $taskRoot
$taskInfraFile = Join-Path ([IO.Path]::GetTempPath()) ('opsflow-infra-' + [guid]::NewGuid() + '.json')
$taskAppFile = Join-Path ([IO.Path]::GetTempPath()) ('opsflow-app-' + [guid]::NewGuid() + '.json')
try {
    Invoke-Checked az @('group','create','--name',$ResourceGroup,'--location',$Location,'--output','none')
    @{ '$schema'='https://schema.management.azure.com/schemas/2019-04-01/deploymentParameters.json#';contentVersion='1.0.0.0';parameters=@{prefix=@{value=$Prefix};databasePassword=@{value=$env:OPSFLOW_DATABASE_PASSWORD}} } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $taskInfraFile
    $taskOutputs = & az deployment group create --name opsflow-infra --resource-group $ResourceGroup --template-file infra/azure/main.bicep --parameters "@$taskInfraFile" --query properties.outputs -o json
    if ($LASTEXITCODE -ne 0) { throw 'Infrastructure deployment failed' }
    $taskValues = $taskOutputs | ConvertFrom-Json
    Invoke-Checked az @('acr','login','--name',$taskValues.registryName.value)
    $taskHost = $taskValues.registryHost.value
    Invoke-Checked docker @('build','-t',"${taskHost}/opsflow-api:$ImageTag",'backend')
    Invoke-Checked docker @('build','-t',"${taskHost}/opsflow-web:$ImageTag",'--build-arg',"VITE_OIDC_AUTHORITY=$OidcIssuer",'--build-arg',"VITE_OIDC_CLIENT_ID=$OidcClientId",'--build-arg',"VITE_OIDC_SCOPE=$OidcScope",'--build-arg',"VITE_OIDC_AUTHORIZATION_AUDIENCE=$OidcAuthorizationAudience",'--build-arg','VITE_API_URL=/api','frontend')
    Invoke-Checked docker @('push',"${taskHost}/opsflow-api:$ImageTag")
    Invoke-Checked docker @('push',"${taskHost}/opsflow-web:$ImageTag")
    $taskParams = @{imageTag=@{value=$ImageTag};oidcIssuer=@{value=$OidcIssuer};oidcJwks=@{value=$OidcJwks};oidcAudience=@{value=$OidcAudience};databasePassword=@{value=$env:OPSFLOW_DATABASE_PASSWORD}}
    foreach($taskKey in @('registryName','environmentName','identityName','postgresName','redisName','serviceBusName','appName')) { $taskParams[$taskKey]=@{value=$taskValues.$taskKey.value} }
    @{ '$schema'='https://schema.management.azure.com/schemas/2019-04-01/deploymentParameters.json#';contentVersion='1.0.0.0';parameters=$taskParams } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $taskAppFile
    Invoke-Checked az @('deployment','group','create','--name','opsflow-app','--resource-group',$ResourceGroup,'--template-file','infra/azure/app.bicep','--parameters',"@$taskAppFile",'--query','properties.outputs.url.value','-o','tsv')
    Write-Host 'Waiting for the public API to become healthy...'
    $taskReady = $false
    for ($taskAttempt = 0; $taskAttempt -lt 30; $taskAttempt++) {
        try {
            $taskHealth = Invoke-RestMethod -Uri "$($taskValues.appUrl.value)/api/health" -TimeoutSec 10
            if ($taskHealth.status -eq 'UP') { $taskReady = $true; break }
        } catch {
            # The first revision may still be starting; retry the public endpoint.
        }
        Start-Sleep -Seconds 5
    }
    if (-not $taskReady) { throw 'The app was provisioned but its public API is not healthy. Check Container Apps startup logs before continuing.' }
    Write-Host 'Public API health: UP'
    Write-Host "Register $($taskValues.appUrl.value)/callback as a SPA redirect URI in your identity provider."
} finally {
    # Only delete the two exact temporary files created by this invocation.
    Remove-Item -LiteralPath $taskInfraFile,$taskAppFile -Force -ErrorAction SilentlyContinue
    Pop-Location
}
