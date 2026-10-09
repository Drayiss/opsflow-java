$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
Push-Location $taskRoot
try {
    $taskReady=$false
    for($i=0;$i -lt 90;$i++) {
        try { $taskHealth=Invoke-RestMethod http://localhost:8080/actuator/health/readiness; if($taskHealth.status -eq 'UP'){ $taskReady=$true;break } } catch { }
        Start-Sleep -Seconds 2
    }
    if(-not $taskReady){throw 'API did not become ready. Check docker compose logs api.'}
    $taskIdentityReady=$false
    for($i=0;$i -lt 60;$i++) {
        try {
            $taskDiscovery=Invoke-RestMethod http://localhost:8180/realms/opsflow/.well-known/openid-configuration
            if($taskDiscovery.issuer -eq 'http://localhost:8180/realms/opsflow'){$taskIdentityReady=$true;break}
        }catch{}
        Start-Sleep -Seconds 2
    }
    if(-not $taskIdentityReady){throw 'The local OIDC realm did not become ready. Check docker compose logs keycloak.'}
    Get-Content -Raw infra/demo.sql | docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U opsflow -d opsflow
    if($LASTEXITCODE -ne 0){throw 'Demo seed failed'}
    Write-Host 'Demo ready at http://localhost:5174. Sign in as alice, bob, or eve with demo-password.'
}finally{Pop-Location}
