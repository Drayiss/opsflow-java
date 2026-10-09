param([int]$Users=50,[string]$Duration='30s',[int]$Repeats=3)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
Push-Location $taskRoot
$taskPreviousCache=$env:CACHE_ENABLED
try {
    New-Item -ItemType Directory -Force perf/results | Out-Null
    Get-Content -Raw perf/seed.sql | docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U opsflow -d opsflow
    if($LASTEXITCODE -ne 0){throw 'Benchmark seed failed'}
    $taskRuns=@()
    foreach($mode in @('baseline','cached')) {
        $env:CACHE_ENABLED=if($mode -eq 'baseline'){'false'}else{'true'}
        docker compose up -d --force-recreate api
        if($LASTEXITCODE -ne 0){throw 'API restart failed'}
        $taskReady=$false
        for($i=0;$i -lt 60;$i++) {
            try { if((Invoke-RestMethod http://localhost:8080/actuator/health/readiness).status -eq 'UP'){$taskReady=$true;break} }catch{}
            Start-Sleep -Seconds 2
        }
        if(-not $taskReady){throw 'API not ready'}
        for($i=1;$i -le $Repeats;$i++) {
            docker run --rm --network opsflow-java_default -v "${taskRoot}/perf:/scripts:ro" -v "${taskRoot}/perf/results:/results" -e BASE_URL=http://api:8080 -e OIDC_URL=http://keycloak:8080 -e "VUS=$Users" -e "DURATION=$Duration" -e "RESULT_FILE=/results/$mode-$i.json" grafana/k6:1.6.0 run /scripts/load.js
            if($LASTEXITCODE -ne 0){throw 'Load test checks failed'}
            $result=Get-Content "perf/results/$mode-$i.json" -Raw | ConvertFrom-Json
            $taskRuns+=@{mode=$mode;run=$i;p95Ms=$result.metrics.summary_latency.values.'p(95)';errorRate=$result.metrics.http_req_failed.values.rate;requests=$result.metrics.http_reqs.values.count}
        }
    }
    function Get-Median($values){$sorted=@($values|Sort-Object);$n=$sorted.Count;if($n%2 -eq 1){return $sorted[[int][math]::Floor($n/2)]};return ($sorted[$n/2-1]+$sorted[$n/2])/2}
    $baseline=Get-Median @($taskRuns|Where-Object mode -eq 'baseline'|ForEach-Object p95Ms)
    $cached=Get-Median @($taskRuns|Where-Object mode -eq 'cached'|ForEach-Object p95Ms)
    $report=@{measuredAt=(Get-Date).ToUniversalTime().ToString('o');users=$Users;duration=$Duration;repeats=$Repeats;incidents=200000;endpoint='/summary';baselineP95Ms=$baseline;cachedP95Ms=$cached;improvementPercent=[math]::Round((($baseline-$cached)/$baseline)*100,2);runs=$taskRuns;scope='Local Docker desktop, same indexed PostgreSQL schema, Redis disabled versus enabled. Read-only summary workload; not an all-endpoint or Azure result.'}
    $taskDockerInfo=docker info --format '{{json .}}'|ConvertFrom-Json
    $taskPom=[xml](Get-Content backend/pom.xml -Raw)
    $report.environment=@{
        dockerCpus=$taskDockerInfo.NCPU;dockerMemoryGiB=[math]::Round($taskDockerInfo.MemTotal/1GB,2)
        apiImageId=(docker inspect opsflow-java-api-1 --format '{{.Image}}')
        network='opsflow-java_default';java=$taskPom.project.properties.'java.version';springBoot=$taskPom.project.parent.version
        postgresImage='postgres:17-alpine';redisImage='redis:7.4-alpine';k6Image='grafana/k6:1.6.0'
        metric='summary_latency; excludes authentication and warm-up'
        httpRequestCounts='Include 50 warm-up summary reads and one authentication request per run'
    }
    $report|ConvertTo-Json -Depth 8|Set-Content perf/results/measured.json
    $report|ConvertTo-Json -Depth 8
}finally{
    $env:CACHE_ENABLED=$taskPreviousCache
    docker compose up -d --force-recreate api
    Pop-Location
}
