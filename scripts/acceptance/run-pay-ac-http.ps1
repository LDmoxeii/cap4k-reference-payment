[CmdletBinding()]
param(
    [int]$Port = 18080,
    [string]$EvidenceRoot = '',
    [string[]]$ScenarioId = @(),
    [switch]$SkipBuild,
    [string]$JarPath = '',
    [string]$JavaPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$registryPath = Join-Path $repositoryRoot 'start\src\test\resources\reference-http\pay-ac-scenarios.json'
$suiteModule = Join-Path $PSScriptRoot 'PayAcHttpSuite.psm1'
$scenarioModule = Join-Path $PSScriptRoot 'PayAcScenarios.psm1'
$integrationEventSinkScript = Join-Path $PSScriptRoot 'reference-integration-event-sink.ps1'
$sourceCommit = '3b66db675356e77c720081e0410baf444b7baa9c'
$runId = 'pay-ac-http-' + [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
if ([string]::IsNullOrWhiteSpace($EvidenceRoot)) {
    $EvidenceRoot = Join-Path $repositoryRoot "build\reference-http-evidence\$runId"
}
$EvidenceRoot = [IO.Path]::GetFullPath($EvidenceRoot)
[IO.Directory]::CreateDirectory($EvidenceRoot) | Out-Null

function Get-Sha256([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }

function Get-BuildRevision {
    $head = (& git -C $repositoryRoot rev-parse HEAD).Trim()
    $dirty = & git -C $repositoryRoot status --short
    if (-not $dirty) { return $head }
    $diff = (& git -C $repositoryRoot diff --no-ext-diff --binary 2>$null) -join "`n"
    $bytes = [Text.Encoding]::UTF8.GetBytes(($dirty -join "`n") + "`n" + $diff)
    $digest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant().Substring(0,16)
    return "$head+dirty-$digest"
}

function Resolve-AcJavaLauncher([string]$RequestedPath) {
    $candidate = $RequestedPath
    if ([string]::IsNullOrWhiteSpace($candidate) -and -not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
    }
    if ([string]::IsNullOrWhiteSpace($candidate)) {
        $candidate = (Get-Command java -ErrorAction Stop).Source
    }
    $candidate = [IO.Path]::GetFullPath($candidate)
    if (-not (Test-Path -LiteralPath $candidate)) { throw "Java launcher not found: $candidate" }
    $versionOutput = (& $candidate -version 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $versionOutput -notmatch 'version\s+"(?<major>\d+)') {
        throw "Unable to determine Java launcher version for ${candidate}: $versionOutput"
    }
    $major = [int]$Matches.major
    if ($major -lt 17) {
        throw "The CAP4K bootJar requires Java 17 or newer, but $candidate reports Java $major. Set -JavaPath or JAVA_HOME to a compatible JDK."
    }
    return [pscustomobject]@{ Path = $candidate; Major = $major; VersionOutput = $versionOutput }
}

function Stop-AcService($Service) {
    if ($null -eq $Service) { return }
    try {
        if ($null -ne $Service.Process -and -not $Service.Process.HasExited) {
            Stop-Process -Id $Service.Process.Id -Force -ErrorAction SilentlyContinue
            $Service.Process.WaitForExit(10000) | Out-Null
        }
    } catch { Write-Warning "Unable to stop CAP4K service PID $($Service.Process.Id): $_" }
    try {
        if ($null -ne $Service.IntegrationEventSinkProcess -and -not $Service.IntegrationEventSinkProcess.HasExited) {
            Stop-Process -Id $Service.IntegrationEventSinkProcess.Id -Force -ErrorAction SilentlyContinue
            $Service.IntegrationEventSinkProcess.WaitForExit(10000) | Out-Null
        }
    } catch { Write-Warning "Unable to stop integration-event sink PID $($Service.IntegrationEventSinkProcess.Id): $_" }
}

function Start-AcService([int]$ServicePort, [string]$Name, [string]$BootJar, [string]$BuildRevision, $JavaLauncher) {
    $logDirectory = Join-Path $EvidenceRoot 'service-logs'
    [IO.Directory]::CreateDirectory($logDirectory) | Out-Null
    $stdout = Join-Path $logDirectory "$Name.stdout.log"
    $stderr = Join-Path $logDirectory "$Name.stderr.log"
    $sinkStdout = Join-Path $logDirectory "$Name.integration-event-sink.stdout.log"
    $sinkStderr = Join-Path $logDirectory "$Name.integration-event-sink.stderr.log"
    $databaseName = ($Name -replace '[^A-Za-z0-9_]','_')
    $datasource = "--spring.datasource.url=jdbc:h2:mem:$databaseName;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    $sinkPort = $ServicePort + 10000
    $pwshLauncher = (Get-Command pwsh -ErrorAction Stop).Source
    $sinkProcess = Start-Process -FilePath $pwshLauncher -ArgumentList @('-NoProfile', '-NonInteractive', '-File', $integrationEventSinkScript, '-Port', $sinkPort) -WorkingDirectory $repositoryRoot -RedirectStandardOutput $sinkStdout -RedirectStandardError $sinkStderr -WindowStyle Hidden -PassThru
    Start-Sleep -Milliseconds 250
    if ($sinkProcess.HasExited) {
        $sinkFailure = if (Test-Path -LiteralPath $sinkStderr) { Get-Content -LiteralPath $sinkStderr -Raw } else { 'no stderr' }
        throw "Reference integration-event sink exited during startup ($($sinkProcess.ExitCode)): $sinkFailure"
    }
    $integrationEventRoute = "--cap4k.ddd.integration.event.http.routes[payment.merchant-settlement.completed.v1]=http://127.0.0.1:$sinkPort"
    try {
        $process = Start-Process -FilePath $JavaLauncher.Path -ArgumentList @('-jar', $BootJar, "--server.port=$ServicePort", $datasource, $integrationEventRoute) -WorkingDirectory $repositoryRoot -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    } catch {
        Stop-AcService ([pscustomobject]@{Process=$null;IntegrationEventSinkProcess=$sinkProcess})
        throw
    }
    $baseUrl = "http://127.0.0.1:$ServicePort"
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds(90)
    $lastFailure = $null
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        if ($sinkProcess.HasExited) {
            $sinkFailure = if (Test-Path -LiteralPath $sinkStderr) { Get-Content -LiteralPath $sinkStderr -Raw } else { 'no stderr' }
            Stop-AcService ([pscustomobject]@{Process=$process;IntegrationEventSinkProcess=$sinkProcess})
            throw "Reference integration-event sink exited during service startup ($($sinkProcess.ExitCode)): $sinkFailure"
        }
        if ($process.HasExited) {
            $tail = @()
            if (Test-Path -LiteralPath $stdout) { $tail += Get-Content -LiteralPath $stdout -Tail 80 }
            if (Test-Path -LiteralPath $stderr) { $tail += Get-Content -LiteralPath $stderr -Tail 80 }
            Stop-AcService ([pscustomobject]@{Process=$process;IntegrationEventSinkProcess=$sinkProcess})
            throw "CAP4K service exited during startup ($($process.ExitCode)):`n$($tail -join "`n")"
        }
        try {
            $probe = Invoke-WebRequest -Uri "$baseUrl/api/reference-fixtures/policy" -Method GET -SkipHttpErrorCheck -TimeoutSec 2
            if ([int]$probe.StatusCode -eq 200) {
                return [pscustomobject]@{
                    Process = $process
                    Pid = $process.Id
                    BaseUrl = $baseUrl
                    DatabaseName = $databaseName
                    Stdout = $stdout
                    Stderr = $stderr
                    BuildRevision = $BuildRevision
                    JarPath = $BootJar
                    JarSha256 = Get-Sha256 $BootJar
                    JavaPath = $JavaLauncher.Path
                    JavaMajor = $JavaLauncher.Major
                    JavaVersion = $JavaLauncher.VersionOutput
                    IntegrationEventSinkProcess = $sinkProcess
                    IntegrationEventSinkPid = $sinkProcess.Id
                    IntegrationEventSinkBaseUrl = "http://127.0.0.1:$sinkPort"
                    IntegrationEventSinkStdout = $sinkStdout
                    IntegrationEventSinkStderr = $sinkStderr
                }
            }
            $lastFailure = "probe HTTP $($probe.StatusCode)"
        } catch { $lastFailure = $_.Exception.Message }
        Start-Sleep -Milliseconds 250
    }
    Stop-AcService ([pscustomobject]@{Process=$process;IntegrationEventSinkProcess=$sinkProcess})
    throw "CAP4K service did not become ready at ${baseUrl}: $lastFailure"
}

function New-RunMetadata($Service, [string]$PhaseRunId) {
    return [pscustomobject]@{
        runId = $PhaseRunId
        service = [ordered]@{
            pid = $Service.Pid
            baseUrl = $Service.BaseUrl
            databaseName = $Service.DatabaseName
            buildRevision = $Service.BuildRevision
            jarPath = $Service.JarPath
            jarSha256 = $Service.JarSha256
            javaPath = $Service.JavaPath
            javaMajor = $Service.JavaMajor
            javaVersion = $Service.JavaVersion
            integrationEventSinkPid = $Service.IntegrationEventSinkPid
            integrationEventSinkBaseUrl = $Service.IntegrationEventSinkBaseUrl
            integrationEventSinkStdout = $Service.IntegrationEventSinkStdout
            integrationEventSinkStderr = $Service.IntegrationEventSinkStderr
            stdout = $Service.Stdout
            stderr = $Service.Stderr
        }
    }
}

function Write-JsonEvidence([string]$Path, $Value) {
    $json = $Value | ConvertTo-Json -Depth 80
    [IO.File]::WriteAllText($Path, $json, [Text.UTF8Encoding]::new($false))
}

if (-not (Test-Path -LiteralPath $registryPath)) { throw "Scenario registry not found: $registryPath" }
$registry = Get-Content -LiteralPath $registryPath -Raw | ConvertFrom-Json -Depth 20
$expectedIds = @(
    1..17 | ForEach-Object { 'PAY-AC-{0:D3}' -f $_ }
    20..29 | ForEach-Object { 'PAY-AC-{0:D3}' -f $_ }
    40..47 | ForEach-Object { 'PAY-AC-{0:D3}' -f $_ }
    60..68 | ForEach-Object { 'PAY-AC-{0:D3}' -f $_ }
    80..88 | ForEach-Object { 'PAY-AC-{0:D3}' -f $_ }
    90..103 | ForEach-Object { 'PAY-AC-{0:D3}' -f $_ }
)
$registeredIds = @($registry.scenarios.id)
$registeredIdSet = (($registeredIds | Sort-Object) -join ',')
$expectedIdSet = (($expectedIds | Sort-Object) -join ',')
if ($registeredIdSet -ne $expectedIdSet) {
    throw "Registry must contain exactly the 67 shared PAY-AC ids. Registered=$($registeredIds -join ',')"
}
if ($registry.sourceCommit -ne $sourceCommit) { throw "Registry source commit drift: $($registry.sourceCommit)" }

$javaLauncher = Resolve-AcJavaLauncher $JavaPath

$selected = if ($ScenarioId.Count -eq 0) { @($registry.scenarios) } else {
    foreach ($id in $ScenarioId) {
        $scenario = @($registry.scenarios | Where-Object id -eq $id)
        if ($scenario.Count -ne 1) { throw "Unknown or duplicate scenario id: $id" }
        $scenario[0]
    }
}

if (-not $SkipBuild) {
    Push-Location $repositoryRoot
    try {
        & .\gradlew.bat :start:bootJar --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Gradle :start:bootJar failed with exit code $LASTEXITCODE" }
    } finally { Pop-Location }
}

if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $candidate = Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'start\build\libs') -Filter '*.jar' |
        Where-Object { $_.Name -notmatch '-plain\.jar$' } | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if ($null -eq $candidate) { throw 'No executable start bootJar was found' }
    $JarPath = $candidate.FullName
} else { $JarPath = [IO.Path]::GetFullPath($JarPath) }
if (-not (Test-Path -LiteralPath $JarPath)) { throw "Boot jar not found: $JarPath" }

$buildRevision = Get-BuildRevision
$mainService = $null
$results = @()
$cleanResults = @()
try {
    $mainService = Start-AcService $Port "$runId-main" $JarPath $buildRevision $javaLauncher
    Import-Module $suiteModule -Force
    Import-Module $scenarioModule -Force
    Set-PayAcHttpRuntime -BaseUrl $mainService.BaseUrl -RunMetadata (New-RunMetadata $mainService $runId)
    foreach ($scenario in $selected) {
        Start-AcEvidenceScope $scenario
        $evidence = $null
        try {
            Invoke-PayAcScenario -ScenarioId $scenario.id
            $evidence = Complete-AcEvidenceScope -Status 'PASSED'
            Write-Host "PASS $($scenario.id) $($scenario.title)"
        } catch {
            $evidence = Complete-AcEvidenceScope -Status 'FAILED' -Failure $_.Exception.ToString()
            Write-Warning "FAIL $($scenario.id) $($scenario.title): $($_.Exception.Message)"
        }
        $path = Join-Path $EvidenceRoot "$($scenario.id).json"
        Write-JsonEvidence $path $evidence
        $results += [pscustomobject]@{scenarioId=$scenario.id;status=$evidence.status;assertionCount=$evidence.assertionCount;evidencePath=$path;normalizedDigest=$evidence.normalizedDigest;failure=$evidence.failure}
    }
} finally { Stop-AcService $mainService }

if ($ScenarioId.Count -eq 0 -or $ScenarioId -contains 'PAY-AC-102') {
    1..2 | ForEach-Object {
        $index = $_
        $service = $null
        try {
            $service = Start-AcService ($Port + $index) "$runId-clean-$index" $JarPath $buildRevision $javaLauncher
            Remove-Module PayAcScenarios -Force -ErrorAction SilentlyContinue
            Remove-Module PayAcHttpSuite -Force -ErrorAction SilentlyContinue
            Import-Module $suiteModule -Force
            Import-Module $scenarioModule -Force
            $cleanRunId = "$runId-clean-$index"
            Set-PayAcHttpRuntime -BaseUrl $service.BaseUrl -RunMetadata (New-RunMetadata $service $cleanRunId)
            $scenario = @($registry.scenarios | Where-Object id -eq 'PAY-AC-102')[0]
            Start-AcEvidenceScope $scenario
            $context = Initialize-AcScenario 'PAY-AC-102'
            $summary = Invoke-PayAcCleanLoop -Context $context -Suffix 'repeatable'
            Add-AcObservation 'cleanLoopNormalized' $summary
            Assert-Ac -Condition ($summary.paymentStatus -eq 'SUCCEEDED' -and $summary.refundStatus -eq 'SUCCEEDED' -and $summary.reconciliationStatus -eq 'COMPLETED' -and $summary.settlementStatus -eq 'SETTLED') -Message 'clean JVM/H2 completes full payment-to-settlement loop' -Actual $summary
            $evidence = Complete-AcEvidenceScope -Status 'PASSED'
            $evidence['cleanLoopIndex'] = $index
            $evidence['normalizedLoopDigest'] = Get-AcNormalizedDigest $summary
            $path = Join-Path $EvidenceRoot "clean-loop-$index.json"
            Write-JsonEvidence $path $evidence
            $cleanResults += [pscustomobject]@{index=$index;status='PASSED';assertionCount=$evidence.assertionCount;evidencePath=$path;normalizedDigest=$evidence.normalizedLoopDigest;summary=$summary}
            Write-Host "PASS CLEAN-LOOP-$index"
        } catch {
            $path = Join-Path $EvidenceRoot "clean-loop-$index.failed.txt"
            [IO.File]::WriteAllText($path, $_.Exception.ToString(), [Text.UTF8Encoding]::new($false))
            $cleanResults += [pscustomobject]@{index=$index;status='FAILED';assertionCount=0;evidencePath=$path;normalizedDigest=$null;summary=$null;failure=$_.Exception.ToString()}
            Write-Warning "FAIL CLEAN-LOOP-${index}: $($_.Exception.Message)"
        } finally { Stop-AcService $service }
    }
}

$cleanRepeatable = $cleanResults.Count -eq 2 -and @($cleanResults | Where-Object status -eq 'PASSED').Count -eq 2 -and $cleanResults[0].normalizedDigest -eq $cleanResults[1].normalizedDigest
$summary = [ordered]@{
    schemaVersion = 1
    sourceCommit = $sourceCommit
    runId = $runId
    buildRevision = $buildRevision
    bootJar = $JarPath
    bootJarSha256 = Get-Sha256 $JarPath
    javaPath = $javaLauncher.Path
    javaMajor = $javaLauncher.Major
    javaVersion = $javaLauncher.VersionOutput
    clientPid = $PID
    expectedScenarioCount = 67
    executedScenarioCount = $results.Count
    passedScenarioCount = @($results | Where-Object status -eq 'PASSED').Count
    failedScenarioCount = @($results | Where-Object status -eq 'FAILED').Count
    results = $results
    cleanLoops = $cleanResults
    cleanLoopsRepeatable = $cleanRepeatable
    generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
}
Write-JsonEvidence (Join-Path $EvidenceRoot 'summary.json') $summary
Write-Host "Evidence: $EvidenceRoot"
Write-Host "PAY-AC: $($summary.passedScenarioCount)/$($summary.executedScenarioCount) passed; clean loops repeatable=$cleanRepeatable"

$allExpectedExecuted = $ScenarioId.Count -gt 0 -or $results.Count -eq 67
$passedWithoutAssertions = @($results | Where-Object { $_.status -eq 'PASSED' -and [int]$_.assertionCount -eq 0 }).Count -gt 0
if ($summary.failedScenarioCount -gt 0 -or $passedWithoutAssertions -or -not $allExpectedExecuted -or (($ScenarioId.Count -eq 0 -or $ScenarioId -contains 'PAY-AC-102') -and -not $cleanRepeatable)) { exit 1 }
exit 0
