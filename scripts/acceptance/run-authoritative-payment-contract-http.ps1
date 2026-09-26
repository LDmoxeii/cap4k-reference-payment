[CmdletBinding()]
param(
    [int]$Port = 18180,
    [string]$EvidenceRoot = '',
    [switch]$SkipBuild,
    [string]$JarPath = '',
    [string]$JavaPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$suiteModule = Join-Path $PSScriptRoot 'PayAcHttpSuite.psm1'
$sinkScript = Join-Path $PSScriptRoot 'reference-integration-event-sink.ps1'
$runId = 'authoritative-payment-http-' + [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
if ([string]::IsNullOrWhiteSpace($EvidenceRoot)) {
    $EvidenceRoot = Join-Path $repositoryRoot "build\reference-http-evidence\$runId"
}
$EvidenceRoot = [IO.Path]::GetFullPath($EvidenceRoot)
[IO.Directory]::CreateDirectory($EvidenceRoot) | Out-Null

function Write-Evidence([string]$Path, $Value) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 80), [Text.UTF8Encoding]::new($false))
}

function New-FieldEvidence($Expected, $Detail, $History) {
    return [ordered]@{
        expected = $Expected
        detail = $Detail
        history = $History
        passed = ($Detail -eq $Expected -and $History -eq $Expected)
    }
}

function Get-CoverageManifest([object[]]$DirectedResults, [string]$CurrentA15Status = 'CURRENT_SCENARIO') {
    $manifest = @()
    for ($number = 1; $number -le 15; $number++) {
        $acceptanceId = "A$number"
        $result = @($DirectedResults | Where-Object scenarioId -eq $acceptanceId | Select-Object -First 1)
        $executionStatus = if ($result.Count -eq 1) { $result[0].status } elseif ($acceptanceId -eq 'A15') { $CurrentA15Status } else { 'NOT_EXECUTED' }
        $title = if ($result.Count -eq 1) { $result[0].title } elseif ($acceptanceId -eq 'A15') { 'clean H2 real HTTP aggregate verification' } else { $acceptanceId }
        $manifest += [pscustomobject][ordered]@{
            acceptanceId = $acceptanceId
            sourceScenarioId = $acceptanceId
            sourceFile = 'docs/comet/changes/align-authoritative-payment-contract/brief.md'
            sourceTitle = $title
            coverageKind = 'directed-real-http'
            executionCheckId = 'http-directed-a1-a15'
            executionStatus = $executionStatus
            evidenceFile = "$acceptanceId.json"
        }
    }

    $specPath = Join-Path $repositoryRoot 'docs\comet\changes\align-authoritative-payment-contract\specs\payment-reference-build\spec.md'
    $payAcScenarios = @(Get-Content -LiteralPath $specPath | ForEach-Object {
        if ($_ -match '^#### Scenario: (?<id>PAY-AC-\d{3})\s+(?<title>.+)$') {
            [pscustomobject]@{ Id = $Matches.id; Title = $Matches.title.Trim() }
        }
    })
    if ($payAcScenarios.Count -ne 67) { throw "Expected 67 PAY-AC scenarios in target Spec, found $($payAcScenarios.Count)" }
    for ($index = 0; $index -lt $payAcScenarios.Count; $index++) {
        $scenario = $payAcScenarios[$index]
        $manifest += [pscustomobject][ordered]@{
            acceptanceId = "A$($index + 16)"
            sourceScenarioId = $scenario.Id
            sourceFile = 'docs/comet/changes/align-authoritative-payment-contract/specs/payment-reference-build/spec.md'
            sourceTitle = $scenario.Title
            coverageKind = 'shared-pay-ac-real-http'
            executionCheckId = 'http-pay-ac-67'
            executionStatus = 'BOUND_RUNTIME_CHECK_REQUIRED'
            evidenceFile = 'build/reference-http-evidence/comet-runtime-iteration-1-pay-ac-67/summary.json'
        }
    }
    return @($manifest)
}

function Resolve-Java([string]$RequestedPath) {
    $candidate = $RequestedPath
    if ([string]::IsNullOrWhiteSpace($candidate) -and -not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
    }
    if ([string]::IsNullOrWhiteSpace($candidate)) { $candidate = (Get-Command java -ErrorAction Stop).Source }
    $candidate = [IO.Path]::GetFullPath($candidate)
    $version = (& $candidate -version 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $version -notmatch 'version\s+"(?<major>\d+)') { throw "Unable to determine Java version: $version" }
    if ([int]$Matches.major -lt 17) { throw "Java 17+ is required, got $($Matches.major)" }
    [pscustomobject]@{ Path = $candidate; Major = [int]$Matches.major; Version = $version }
}

function Stop-ServiceProcess($Service) {
    if ($null -eq $Service) { return }
    foreach ($process in @($Service.Process, $Service.SinkProcess)) {
        if ($null -ne $process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            $process.WaitForExit(10000) | Out-Null
        }
    }
}

function Start-ServiceProcess([int]$ServicePort, [string]$BootJar, $Java) {
    $logs = Join-Path $EvidenceRoot 'service-logs'
    [IO.Directory]::CreateDirectory($logs) | Out-Null
    $stdout = Join-Path $logs 'service.stdout.log'
    $stderr = Join-Path $logs 'service.stderr.log'
    $sinkStdout = Join-Path $logs 'sink.stdout.log'
    $sinkStderr = Join-Path $logs 'sink.stderr.log'
    $sinkPort = $ServicePort + 10000
    $pwsh = (Get-Command pwsh -ErrorAction Stop).Source
    $sink = Start-Process -FilePath $pwsh -ArgumentList @('-NoProfile','-NonInteractive','-File',$sinkScript,'-Port',$sinkPort) -WorkingDirectory $repositoryRoot -RedirectStandardOutput $sinkStdout -RedirectStandardError $sinkStderr -WindowStyle Hidden -PassThru
    Start-Sleep -Milliseconds 250
    if ($sink.HasExited) { throw "Integration-event sink exited during startup: $(Get-Content -LiteralPath $sinkStderr -Raw)" }
    $databaseName = ($runId -replace '[^A-Za-z0-9_]','_')
    $datasource = "--spring.datasource.url=jdbc:h2:mem:$databaseName;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    $route = "--cap4k.ddd.integration.event.http.routes[payment.merchant-settlement.completed.v1]=http://127.0.0.1:$sinkPort"
    $process = Start-Process -FilePath $Java.Path -ArgumentList @('-jar',$BootJar,"--server.port=$ServicePort",$datasource,$route) -WorkingDirectory $repositoryRoot -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    $service = [pscustomobject]@{ Process=$process; SinkProcess=$sink; BaseUrl="http://127.0.0.1:$ServicePort"; DatabaseName=$databaseName; Stdout=$stdout; Stderr=$stderr; SinkStdout=$sinkStdout; SinkStderr=$sinkStderr }
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds(90)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        if ($process.HasExited) {
            $tail = if (Test-Path -LiteralPath $stderr) { Get-Content -LiteralPath $stderr -Tail 100 } else { @() }
            Stop-ServiceProcess $service
            throw "CAP4K service exited during startup:`n$($tail -join "`n")"
        }
        try {
            $probe = Invoke-WebRequest -Uri "$($service.BaseUrl)/api/reference-fixtures/policy" -Method GET -SkipHttpErrorCheck -TimeoutSec 2
            if ([int]$probe.StatusCode -eq 200) { return $service }
        } catch { }
        Start-Sleep -Milliseconds 250
    }
    Stop-ServiceProcess $service
    throw "CAP4K service did not become ready at $($service.BaseUrl)"
}

function Register-BillRevision($Context, [string]$BillIdentity, [string]$Revision, [object[]]$Records, [string]$Fingerprint = '') {
    if ([string]::IsNullOrWhiteSpace($Fingerprint)) { $Fingerprint = "$BillIdentity-$Revision-fingerprint" }
    return (Invoke-AcHttp POST '/api/reference-fixtures/bills' @{
        channelId='C-001'; billIdentity=$BillIdentity; businessDate=$Context.BusinessDate; currency='CNY'; businessTimezone='Asia/Shanghai'
        revision=$Revision; completeness='COMPLETE'; rawEvidence="evidence://$BillIdentity/$Revision"; payloadFingerprint=$Fingerprint
        publishedAt=$Context.BaseInstant.AddHours(10 + [int]$Revision).ToString('o'); records=$Records
    } @(200)).Body
}

function New-SettlementFixture($Context, [string]$Suffix) {
    $payment = New-AcSucceededPayment $Context 100 "settlement-$Suffix"
    $record = New-AcBillRecord "$($Context.Alias)-record-$Suffix" 'PAYMENT' $payment.TransactionId 100 $payment.OccurredAt
    Register-AcBillAndSignal $Context @($record) '1' "settlement-$Suffix" | Out-Null
    $prepared = New-AcPreparedSettlement $Context "settlement-$Suffix"
    Confirm-AcSettlement $Context $prepared.settlementId "settlement-$Suffix" | Out-Null
    return [pscustomobject]@{ Payment=$payment; SettlementId=$prepared.settlementId }
}

function Configure-SettlementScript([string]$ExecutionId, [string]$Script) {
    return (Invoke-AcHttp POST '/api/reference-fixtures/settlement-executor-script' @{executionId=$ExecutionId;script=$Script} @(200)).Body
}

function Execute-Settlement($Context, [string]$SettlementId, [string]$ExecutionId, [string]$Key, [int[]]$ExpectedStatus = @(200)) {
    return Invoke-AcHttp POST "/api/merchant-settlements/$SettlementId/executions" @{
        merchantId=$Context.MerchantId; executionId=$ExecutionId; executionChannelId='C-001'; idempotencyKey=$Key
    } $ExpectedStatus
}

function Get-Payment([string]$PaymentId) {
    return (Invoke-AcHttp GET "/api/payments/$PaymentId" $null @(200)).Body
}

function Get-Settlement([string]$SettlementId) {
    return (Invoke-AcHttp GET "/api/merchant-settlements/$SettlementId" $null @(200)).Body
}

function Get-SettlementScript([string]$ExecutionId) {
    return (Invoke-AcHttp GET "/api/reference-fixtures/settlement-executor-script/$ExecutionId" $null @(200)).Body
}

function Reset-SettlementScript([string]$ExecutionId) {
    return (Invoke-AcHttp POST '/api/reference-fixtures/settlement-executor-script/reset' @{executionId=$ExecutionId} @(200)).Body
}

function Get-Operation([string]$OperationUrl) {
    return (Invoke-AcHttp GET $OperationUrl $null @(200)).Body.operation
}

function Search-SettlementNotifications([string]$MerchantId) {
    return (Invoke-AcHttp POST '/api/merchant-notifications/search' @{merchantId=$MerchantId;sourceKind='SETTLEMENT';pageSize=100} @(200)).Body
}

function Assert-SettlementComposition($Before, $After, [string]$Label) {
    foreach ($name in @('scopeIdentity','effectiveScopeIdentity','grossMoney','refundMoney','feeMoney','adjustmentMoney','netMoney','lines')) {
        $beforeValue = $Before.$name | ConvertTo-Json -Depth 30 -Compress
        $afterValue = $After.$name | ConvertTo-Json -Depth 30 -Compress
        Assert-AcEqual $afterValue $beforeValue "$Label preserves $name"
    }
}

function New-ScenarioContext([int]$Number) {
    return Initialize-AcScenario ('PAY-AC-{0:D3}' -f (900 + $Number))
}

$results = @()
function Run-Scenario([string]$Id, [string]$Title, [scriptblock]$Body) {
    $scenario = [pscustomobject]@{ id=$Id; title=$Title; fixture='clean-h2-real-http'; automationTestId="HTTP-$Id" }
    Start-AcEvidenceScope $scenario
    try {
        & $Body
        $evidence = Complete-AcEvidenceScope -Status 'PASSED'
        Write-Host "PASS $Id $Title"
    } catch {
        $evidence = Complete-AcEvidenceScope -Status 'FAILED' -Failure $_.Exception.ToString()
        Write-Warning "FAIL $Id $Title : $($_.Exception.Message)"
    }
    $path = Join-Path $EvidenceRoot "$Id.json"
    Write-Evidence $path $evidence
    $script:results += [pscustomobject][ordered]@{
        scenarioId = $Id
        title = $Title
        status = $evidence.status
        requestCount = @($evidence.http).Count
        responseCount = @($evidence.http | Where-Object { $null -ne $_.responseStatus }).Count
        assertionCount = $evidence.assertionCount
        evidenceFile = "$Id.json"
        evidencePath = $path
        normalizedDigest = $evidence.normalizedDigest
        failure = $evidence.failure
    }
}

if (-not $SkipBuild) {
    Push-Location $repositoryRoot
    try {
        & .\gradlew.bat :start:bootJar --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Gradle :start:bootJar failed: $LASTEXITCODE" }
    } finally { Pop-Location }
}
if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $jar = Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'start\build\libs') -Filter '*.jar' | Where-Object Name -notmatch '-plain\.jar$' | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if ($null -eq $jar) { throw 'No executable bootJar found' }
    $JarPath = $jar.FullName
}
$JarPath = [IO.Path]::GetFullPath($JarPath)
$java = Resolve-Java $JavaPath
$service = $null
try {
    $service = Start-ServiceProcess $Port $JarPath $java
    Import-Module $suiteModule -Force
    $metadata = [pscustomobject]@{ runId=$runId; service=[ordered]@{pid=$service.Process.Id;baseUrl=$service.BaseUrl;databaseName=$service.DatabaseName;jarPath=$JarPath;javaPath=$java.Path;stdout=$service.Stdout;stderr=$service.Stderr;clientPid=$PID} }
    Set-PayAcHttpRuntime -BaseUrl $service.BaseUrl -RunMetadata $metadata

    Run-Scenario 'A1' 'authoritative bill current revision and complete ascending history' {
        $c=New-ScenarioContext 1;$id="BILL-$($c.Alias)-A1";$r=New-AcBillRecord 'A1-R1' 'PAYMENT' 'TX-A1' 10 $c.BaseInstant.ToString('o')
        $one=Register-BillRevision $c $id '1' @($r);Register-BillRevision $c $id '2' @($r)|Out-Null
        $detail=(Invoke-AcHttp GET "/api/authoritative-bills/$($one.billId)" $null @(200)).Body
        $history=(Invoke-AcHttp GET "/api/authoritative-bills/$($one.billId)/revisions" $null @(200)).Body
        Assert-AcEqual $detail.currentRevision '2' 'bill detail exposes current revision 2';Assert-AcEqual ($detail.revisions.revision -join ',') '1,2' 'bill detail orders complete history revision ASC'
        Assert-AcEqual ($history.revisions.revision -join ',') '1,2' 'revision endpoint exposes complete ascending history';Add-AcObservation 'bill' $detail
    }
    Run-Scenario 'A2' 'revision metadata and immutable record evidence are public' {
        $c=New-ScenarioContext 2
        $id="BILL-$($c.Alias)-A2"
        $records1=@(
            (New-AcBillRecord 'A2-P' 'PAYMENT' 'TX-A2-P' 12.34 $c.BaseInstant.ToString('o') 'SUCCEEDED'),
            (New-AcBillRecord 'A2-R' 'REFUND' 'TX-A2-R' 2.34 $c.BaseInstant.AddMinutes(1).ToString('o') 'PENDING')
        )
        $records2=@(
            (New-AcBillRecord 'A2-P' 'PAYMENT' 'TX-A2-P' 12.34 $c.BaseInstant.ToString('o') 'SUCCEEDED'),
            (New-AcBillRecord 'A2-R2' 'REFUND' 'TX-A2-R2' 3.45 $c.BaseInstant.AddMinutes(3).ToString('o') 'FAILED')
        )
        $revisionBodies=@(
            @{channelId='C-001';billIdentity=$id;businessDate=$c.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai';revision='1';completeness='INCOMPLETE';rawEvidence="evidence://$id/partial";payloadFingerprint="$id-partial-fingerprint";publishedAt=$c.BaseInstant.AddHours(11).ToString('o');records=$records1},
            @{channelId='C-001';billIdentity=$id;businessDate=$c.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai';revision='2';completeness='COMPLETE';rawEvidence="evidence://$id/complete";payloadFingerprint="$id-complete-fingerprint";publishedAt=$c.BaseInstant.AddHours(13).ToString('o');records=$records2}
        )
        $registered=(Invoke-AcHttp POST '/api/reference-fixtures/bills' $revisionBodies[0] @(200)).Body
        $first=(Invoke-AcHttp GET "/api/authoritative-bills/$($registered.billId)" $null @(200)).Body
        Invoke-AcHttp POST '/api/reference-fixtures/bills' $revisionBodies[1] @(200)|Out-Null
        $bill=(Invoke-AcHttp GET "/api/authoritative-bills/$($registered.billId)" $null @(200)).Body
        $history=(Invoke-AcHttp GET "/api/authoritative-bills/$($registered.billId)/revisions" $null @(200)).Body
        Assert-AcEqual $bill.currentRevision '2' 'later revision is current'
        Assert-AcEqual ($bill.revisions.revision -join ',') '1,2' 'detail revisions are ASC and complete'
        Assert-AcEqual ($history.revisions.revision -join ',') '1,2' 'history endpoint revisions are ASC and complete'
        Assert-AcEqual $bill.revisions[0].revisionId $first.revisions[0].revisionId 'old immutable revision identity survives publication'
        for ($i=0; $i -lt 2; $i++) {
            $expected=$revisionBodies[$i]
            foreach ($query in @($bill,$history)) {
                $actual=$query.revisions[$i]
                foreach ($field in @('revision','completeness','payloadFingerprint','rawEvidence')) {
                    Assert-AcEqual $actual.$field $expected.$field "revision $($expected.revision) exposes $field"
                }
                Assert-AcEqual ([DateTimeOffset]$actual.publishedAt).ToUnixTimeMilliseconds() ([DateTimeOffset]::Parse([string]$expected.publishedAt).ToUnixTimeMilliseconds()) "revision $($expected.revision) exposes publishedAt instant"
                Assert-AcEqual $actual.records.Count $expected.records.Count "revision $($expected.revision) retains every record"
                for ($j=0; $j -lt $expected.records.Count; $j++) {
                    $record=$actual.records[$j];$source=$expected.records[$j]
                    foreach ($field in @('recordIdentity','transactionKind','rawStatus','rawEvidence')) {
                        Assert-AcEqual $record.$field $source.$field "revision $($expected.revision) record $j exposes $field"
                    }
                    Assert-AcEqual ([DateTimeOffset]$record.occurredAt).ToUnixTimeMilliseconds() ([DateTimeOffset]::Parse([string]$source.occurredAt).ToUnixTimeMilliseconds()) "revision $($expected.revision) record $j exposes occurredAt instant"
                    Assert-AcEqual ([DateTimeOffset]$record.receivedAt).ToUnixTimeMilliseconds() ([DateTimeOffset]::Parse([string]$source.receivedAt).ToUnixTimeMilliseconds()) "revision $($expected.revision) record $j exposes receivedAt instant"
                    Assert-AcEqual $record.externalTransactionIdentity $source.channelTransactionIdentity "revision $($expected.revision) record $j exposes external transaction identity"
                    Assert-AcEqual $record.money.currency $source.money.currency "revision $($expected.revision) record $j exposes Money currency"
                    Assert-AcEqual $record.money.amountMinor $source.money.amountMinor "revision $($expected.revision) record $j exposes Money minor amount"
                    Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace([string]$record.recordId)) -Message "revision $($expected.revision) record $j has durable identity" -Actual $record
                }
            }
        }
        Assert-AcEqual ($bill.revisions[0] | ConvertTo-Json -Depth 30 -Compress) ($first.revisions[0] | ConvertTo-Json -Depth 30 -Compress) 'new revision did not mutate earlier published revision'
        $orderEvidence=[ordered]@{
            expected=@('1','2')
            detail=@($bill.revisions.revision)
            history=@($history.revisions.revision)
            passed=(($bill.revisions.revision -join ',') -eq '1,2' -and ($history.revisions.revision -join ',') -eq '1,2')
        }
        $matrixPassed=[bool]$orderEvidence.passed
        $revisionFieldMatrix=@()
        for ($i=0; $i -lt $revisionBodies.Count; $i++) {
            $expected=$revisionBodies[$i]
            $detailRevision=$bill.revisions[$i]
            $historyRevision=$history.revisions[$i]
            $publishedAt=New-FieldEvidence `
                ([DateTimeOffset]::Parse([string]$expected.publishedAt).ToUnixTimeMilliseconds()) `
                ([DateTimeOffset]$detailRevision.publishedAt).ToUnixTimeMilliseconds() `
                ([DateTimeOffset]$historyRevision.publishedAt).ToUnixTimeMilliseconds()
            $revisionFields=[ordered]@{
                revision=New-FieldEvidence $expected.revision $detailRevision.revision $historyRevision.revision
                publishedAt=$publishedAt
                completeness=New-FieldEvidence $expected.completeness $detailRevision.completeness $historyRevision.completeness
                payloadFingerprint=New-FieldEvidence $expected.payloadFingerprint $detailRevision.payloadFingerprint $historyRevision.payloadFingerprint
                evidence=[ordered]@{
                    apiField='rawEvidence'
                    expected=$expected.rawEvidence
                    detail=$detailRevision.rawEvidence
                    history=$historyRevision.rawEvidence
                    passed=($detailRevision.rawEvidence -eq $expected.rawEvidence -and $historyRevision.rawEvidence -eq $expected.rawEvidence)
                }
                recordCount=New-FieldEvidence $expected.records.Count $detailRevision.records.Count $historyRevision.records.Count
            }
            $revisionPassed=$true
            foreach ($comparison in $revisionFields.Values) { $revisionPassed = $revisionPassed -and [bool]$comparison.passed }
            $recordMatrix=@()
            for ($j=0; $j -lt $expected.records.Count; $j++) {
                $source=$expected.records[$j]
                $detailRecord=$detailRevision.records[$j]
                $historyRecord=$historyRevision.records[$j]
                $expectedOccurredAt=[DateTimeOffset]::Parse([string]$source.occurredAt).ToUnixTimeMilliseconds()
                $moneyPassed=(
                    $detailRecord.money.currency -eq $source.money.currency -and
                    $detailRecord.money.amountMinor -eq $source.money.amountMinor -and
                    $historyRecord.money.currency -eq $source.money.currency -and
                    $historyRecord.money.amountMinor -eq $source.money.amountMinor
                )
                $recordFields=[ordered]@{
                    money=[ordered]@{
                        expected=[ordered]@{currency=$source.money.currency;amountMinor=$source.money.amountMinor}
                        detail=$detailRecord.money
                        history=$historyRecord.money
                        passed=$moneyPassed
                    }
                    rawStatus=New-FieldEvidence $source.rawStatus $detailRecord.rawStatus $historyRecord.rawStatus
                    occurredAt=New-FieldEvidence $expectedOccurredAt ([DateTimeOffset]$detailRecord.occurredAt).ToUnixTimeMilliseconds() ([DateTimeOffset]$historyRecord.occurredAt).ToUnixTimeMilliseconds()
                    rawEvidence=New-FieldEvidence $source.rawEvidence $detailRecord.rawEvidence $historyRecord.rawEvidence
                }
                $recordPassed=$true
                foreach ($comparison in $recordFields.Values) { $recordPassed = $recordPassed -and [bool]$comparison.passed }
                $revisionPassed = $revisionPassed -and $recordPassed
                $recordMatrix += [pscustomobject][ordered]@{
                    recordIdentity=$source.recordIdentity
                    fields=$recordFields
                    passed=$recordPassed
                }
            }
            $matrixPassed = $matrixPassed -and $revisionPassed
            $revisionFieldMatrix += [pscustomobject][ordered]@{
                revision=$expected.revision
                fields=$revisionFields
                records=$recordMatrix
                passed=$revisionPassed
            }
        }
        $firstRevisionJson=$first.revisions[0] | ConvertTo-Json -Depth 30 -Compress
        $detailRevisionJson=$bill.revisions[0] | ConvertTo-Json -Depth 30 -Compress
        $historyRevisionJson=$history.revisions[0] | ConvertTo-Json -Depth 30 -Compress
        $immutabilityEvidence=[ordered]@{
            comparedFields=@('revisionId','revision','publishedAt','completeness','payloadFingerprint','rawEvidence','records')
            firstRead=$first.revisions[0]
            afterSecondPublication=$bill.revisions[0]
            historyRead=$history.revisions[0]
            passed=($detailRevisionJson -eq $firstRevisionJson -and $historyRevisionJson -eq $firstRevisionJson)
        }
        $matrixPassed = $matrixPassed -and [bool]$immutabilityEvidence.passed
        $fieldMatrix=[ordered]@{
            revisionOrder=$orderEvidence
            revisions=$revisionFieldMatrix
            revision1UnchangedAfterRevision2=$immutabilityEvidence
            passed=$matrixPassed
        }
        Assert-Ac -Condition $matrixPassed -Message 'A2 explicit revision and record field assertion matrix passes' -Actual $fieldMatrix
        Add-AcObservation 'revisionFieldAssertions' $fieldMatrix
        Add-AcObservation 'authoritativeBill' $bill
        Add-AcObservation 'immutableHistory' $history
    }
    Run-Scenario 'A3' 'same revision is idempotent and conflicting content is rejected' {
        $c=New-ScenarioContext 3;$id="BILL-$($c.Alias)-A3";$records=@(New-AcBillRecord 'A3-R' 'PAYMENT' 'TX-A3' 10 $c.BaseInstant.ToString('o'));$first=Register-BillRevision $c $id '2' $records;$replay=Register-BillRevision $c $id '2' $records
        $conflict=Invoke-AcHttp POST '/api/reference-fixtures/bills' @{channelId='C-001';billIdentity=$id;businessDate=$c.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai';revision='2';completeness='COMPLETE';rawEvidence='evidence://conflict';payloadFingerprint="$id-2-fingerprint";publishedAt=$c.BaseInstant.AddHours(12).ToString('o');records=$records} @(400,409)
        $detail=(Invoke-AcHttp GET "/api/authoritative-bills/$($first.billId)" $null @(200)).Body
        Assert-Ac -Condition ([bool]$replay.idempotentReplay) -Message 'same revision content is idempotent' -Actual $replay;Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace((Get-AcErrorCode $conflict.Body))) -Message 'conflicting revision returns stable ApiError' -Actual $conflict.Body;Assert-AcEqual $detail.revisions.Count 1 'conflict does not add or overwrite revision'
    }
    Run-Scenario 'A4' 'late lower revision never regresses current and unknown bill is stable' {
        $c=New-ScenarioContext 4;$id="BILL-$($c.Alias)-A4";$r=New-AcBillRecord 'A4-R' 'PAYMENT' 'TX-A4' 10 $c.BaseInstant.ToString('o');$two=Register-BillRevision $c $id '2' @($r);Register-BillRevision $c $id '1' @($r)|Out-Null
        $detail=(Invoke-AcHttp GET "/api/authoritative-bills/$($two.billId)" $null @(200)).Body;$missing=Invoke-AcHttp GET '/api/authoritative-bills/00000000-0000-7000-8000-000000000999' $null @(404)
        Assert-AcEqual $detail.currentRevision '2' 'late lower revision does not regress current';Assert-AcEqual ($detail.revisions.revision -join ',') '1,2' 'late lower revision remains in ascending history';Assert-Ac -Condition ((Get-AcErrorCode $missing.Body) -match 'BILL|NOT_FOUND') -Message 'unknown bill returns stable ApiError' -Actual $missing.Body
    }
    Run-Scenario 'A5' 'single expired payment closes with a real READ_ONCE receipt' {
        $c=New-ScenarioContext 5;$payment=New-AcPayment $c 10 'CNY' 'expired' 'expired' $c.BaseInstant.AddMinutes(1);$other=New-AcPayment $c 11 'CNY' 'other-expired' 'other-expired' $c.BaseInstant.AddMinutes(1)
        $otherBefore=Get-Payment $other.paymentId;Set-AcClock $c.BaseInstant.AddMinutes(2)|Out-Null
        $closed=(Invoke-AcHttp POST "/api/payments/$($payment.paymentId)/close-expired" @{merchantId=$c.MerchantId;idempotencyKey="$($c.Alias)-close"} @(200)).Body
        $operation=Get-Operation $closed.receipt.readAfter.operationUrl
        $target=(Invoke-AcHttp GET $closed.receipt.readAfter.resourceUrl $null @(200)).Body
        $untouched=Get-Payment $other.paymentId
        Assert-AcEqual $closed.paymentStatus 'CLOSED' 'addressed expired payment closes'
        Assert-AcEqual $closed.receipt.readAfter.mode 'READ_ONCE' 'close returns READ_ONCE'
        Assert-AcEqual $closed.receipt.readAfter.resourceUrl "/api/payments/$($payment.paymentId)" 'readAfter binds target Payment'
        Assert-AcEqual $operation.operationId $closed.receipt.operationId 'returned Operation is publicly readable'
        Assert-AcEqual $operation.status 'SUCCEEDED' 'close Operation succeeds'
        Assert-AcEqual $operation.resource.resourceId $payment.paymentId 'Operation references addressed Payment'
        Assert-AcEqual $target.status 'CLOSED' 'readAfter resource has CLOSED status'
        Assert-AcEqual $untouched.status $otherBefore.status 'second expired Payment remains unchanged'
        Assert-AcEqual $untouched.closedAt $otherBefore.closedAt 'second expired Payment has no close fact'
        Assert-AcEqual $untouched.paymentId $other.paymentId 'second expired Payment remains separately queryable'
        Add-AcObservation 'targetPayment' $target;Add-AcObservation 'otherPayment' $untouched;Add-AcObservation 'closeOperation' $operation
    }
    Run-Scenario 'A6' 'single payment close replay and binding conflict are stable' {
        $c=New-ScenarioContext 6;$p1=New-AcPayment $c 10 'CNY' 'one' 'one' $c.BaseInstant.AddMinutes(1);$p2=New-AcPayment $c 11 'CNY' 'two' 'two' $c.BaseInstant.AddMinutes(1);$p2Before=Get-Payment $p2.paymentId;Set-AcClock $c.BaseInstant.AddMinutes(2)|Out-Null;$body=@{merchantId=$c.MerchantId;idempotencyKey="$($c.Alias)-close"}
        $first=(Invoke-AcHttp POST "/api/payments/$($p1.paymentId)/close-expired" $body @(200)).Body;$before=Get-Operation $first.receipt.readAfter.operationUrl
        $replay=(Invoke-AcHttp POST "/api/payments/$($p1.paymentId)/close-expired" $body @(200)).Body
        $paymentConflict=Invoke-AcHttp POST "/api/payments/$($p2.paymentId)/close-expired" $body @(409)
        $merchantConflict=Invoke-AcHttp POST "/api/payments/$($p1.paymentId)/close-expired" @{merchantId="$($c.MerchantId)-other";idempotencyKey=$body.idempotencyKey} @(409)
        $original=Get-Payment $p1.paymentId;$other=Get-Payment $p2.paymentId;$after=Get-Operation $first.receipt.readAfter.operationUrl
        Assert-AcEqual $replay.receipt.operationId $first.receipt.operationId 'close replay returns the same Operation'
        Assert-AcEqual $replay.receipt.acceptanceStatus 'ALREADY_ACCEPTED' 'close replay is explicit'
        Assert-AcEqual (Get-AcErrorCode $paymentConflict.Body) 'IDEMPOTENCY_CONFLICT' 'same key cannot bind another payment'
        Assert-AcEqual $paymentConflict.Body.details.operationId $first.receipt.operationId 'payment rebinding conflict references original Operation'
        Assert-AcEqual (Get-AcErrorCode $merchantConflict.Body) 'PAYMENT_MERCHANT_CONFLICT' 'same key cannot cross merchant scope'
        Assert-AcEqual $merchantConflict.Body.details.paymentId $p1.paymentId 'merchant scope conflict references addressed Payment'
        Assert-AcEqual $original.status 'CLOSED' 'original Payment remains closed once'
        Assert-AcEqual $other.status $p2Before.status 'other Payment receives no close effect'
        Assert-AcEqual $other.closedAt $p2Before.closedAt 'other Payment has no close fact'
        Assert-AcEqual $after.operationId $before.operationId 'conflicts do not replace Operation'
        Assert-AcEqual $after.updatedAt $before.updatedAt 'conflicts do not mutate Operation'
        Assert-AcEqual $after.resource.resourceId $p1.paymentId 'Operation remains bound to original Payment'
        Add-AcObservation 'originalPayment' $original;Add-AcObservation 'otherPayment' $other;Add-AcObservation 'operation' $after
    }
    Run-Scenario 'A7' 'expired payment with an unresolved attempt remains unknown and opens review' {
        $c=New-ScenarioContext 7;Invoke-AcHttp POST '/api/reference-fixtures/payment-channel-script' @{channelId='C-001';script='NO_RESULT'} @(200)|Out-Null;$p=New-AcPayment $c 10 'CNY' 'pending' 'pending' $c.BaseInstant.AddMinutes(1);$a=New-AcPaymentAttempt $c $p.paymentId 'pending';Submit-AcPaymentAttempt $c $p.paymentId $a.paymentAttemptId 'pending'|Out-Null;Set-AcClock $c.BaseInstant.AddMinutes(10)|Out-Null
        $closed=(Invoke-AcHttp POST "/api/payments/$($p.paymentId)/close-expired" @{merchantId=$c.MerchantId;idempotencyKey="$($c.Alias)-close"} @(200)).Body;$detail=(Invoke-AcHttp GET "/api/payments/$($p.paymentId)" $null @(200)).Body;$reviews=(Invoke-AcHttp POST '/api/manual-reviews/search' @{merchantId=$c.MerchantId;relatedResourceId=$p.paymentId;pageSize=20} @(200)).Body
        Assert-AcEqual $detail.status 'RESULT_UNKNOWN' 'pending attempt prevents close';Assert-Ac -Condition ($reviews.items.Count -ge 1) -Message 'overdue unresolved attempt opens ManualReview' -Actual $reviews;Assert-AcEqual $closed.receipt.readAfter.mode 'READ_ONCE' 'accepted close adjudication has a real receipt'
    }
    Run-Scenario 'A8' 'not expired and succeeded payments reject without OperationReceipt' {
        $c=New-ScenarioContext 8;$open=New-AcPayment $c 10 'CNY' 'open' 'open' $c.BaseInstant.AddMinutes(20)
        $openBefore=Get-Payment $open.paymentId;$openOperationBefore=Get-Operation $open.receipt.readAfter.operationUrl
        $openError=Invoke-AcHttp POST "/api/payments/$($open.paymentId)/close-expired" @{merchantId=$c.MerchantId;idempotencyKey="$($c.Alias)-open-close"} @(409)
        $success=New-AcSucceededPayment $c 10 'success'
        $successBefore=Get-Payment $success.PaymentId;$successOperationBefore=Get-Operation $success.Created.receipt.readAfter.operationUrl
        $notificationsBefore=(Invoke-AcHttp POST '/api/merchant-notifications/search' @{merchantId=$c.MerchantId;sourceKind='PAYMENT';pageSize=100} @(200)).Body
        $settlementsBefore=(Invoke-AcHttp POST '/api/merchant-settlements/search' @{merchantId=$c.MerchantId;pageSize=100} @(200)).Body
        Set-AcClock $c.BaseInstant.AddMinutes(40)|Out-Null
        $successError=Invoke-AcHttp POST "/api/payments/$($success.PaymentId)/close-expired" @{merchantId=$c.MerchantId;idempotencyKey="$($c.Alias)-success-close"} @(409)
        $openAfter=Get-Payment $open.paymentId;$successAfter=Get-Payment $success.PaymentId
        $openOperationAfter=Get-Operation $open.receipt.readAfter.operationUrl;$successOperationAfter=Get-Operation $success.Created.receipt.readAfter.operationUrl
        $notificationsAfter=(Invoke-AcHttp POST '/api/merchant-notifications/search' @{merchantId=$c.MerchantId;sourceKind='PAYMENT';pageSize=100} @(200)).Body
        $settlementsAfter=(Invoke-AcHttp POST '/api/merchant-settlements/search' @{merchantId=$c.MerchantId;pageSize=100} @(200)).Body
        Assert-AcEqual (Get-AcErrorCode $openError.Body) 'PAYMENT_NOT_EXPIRED' 'not-expired payment rejects synchronously'
        Assert-AcEqual (Get-AcErrorCode $successError.Body) 'INVALID_STATE_TRANSITION' 'succeeded payment rejects synchronously'
        Assert-AcEqual $openAfter.status $openBefore.status 'not-expired rejection leaves Payment state unchanged'
        Assert-AcEqual $openAfter.attemptCount $openBefore.attemptCount 'not-expired rejection creates no attempt'
        Assert-AcEqual $openAfter.closedAt $null 'not-expired rejection writes no close fact'
        Assert-AcEqual $successAfter.status 'SUCCEEDED' 'succeeded rejection retains success state'
        Assert-AcEqual $successAfter.successFactFormed $successBefore.successFactFormed 'succeeded rejection retains success fact'
        Assert-AcEqual $successAfter.channelTransactionId $successBefore.channelTransactionId 'succeeded rejection preserves transaction'
        Assert-AcEqual $successAfter.attemptCount $successBefore.attemptCount 'succeeded rejection creates no attempt'
        Assert-AcEqual $successAfter.notificationReceiveCount $successBefore.notificationReceiveCount 'succeeded rejection creates no result notification'
        Assert-AcEqual $successAfter.closedAt $null 'succeeded rejection writes no close fact'
        Assert-AcEqual $openOperationAfter.updatedAt $openOperationBefore.updatedAt 'not-expired rejection does not mutate existing Operation'
        Assert-AcEqual $successOperationAfter.updatedAt $successOperationBefore.updatedAt 'succeeded rejection does not mutate existing Operation'
        Assert-AcEqual $notificationsAfter.items.Count $notificationsBefore.items.Count 'rejections create no merchant notification'
        Assert-AcEqual $settlementsAfter.items.Count $settlementsBefore.items.Count 'rejections create no settlement'
        Add-AcObservation 'notExpiredPayment' $openAfter;Add-AcObservation 'succeededPayment' $successAfter
    }
    Run-Scenario 'A9' 'settlement executor configure read reset and frozen consumption' {
        $c=New-ScenarioContext 9
        foreach ($kind in @('SUCCESS','FAILURE','UNKNOWN','NO_RESULT')) {
            $probeId="EXEC-A9-PROBE-$kind"
            $configured=Configure-SettlementScript $probeId $kind
            $again=Configure-SettlementScript $probeId $kind
            $read=Get-SettlementScript $probeId
            Assert-AcEqual $configured.script $kind "$kind configure is public"
            Assert-AcEqual $again.script $kind "$kind duplicate configure is idempotent"
            Assert-AcEqual $read.script $kind "$kind read exposes configured script"
            Assert-AcEqual $read.consumed $false "$kind is not consumed before Execute"
            $reset=Reset-SettlementScript $probeId;$resetAgain=Reset-SettlementScript $probeId;$afterReset=Get-SettlementScript $probeId
            Assert-AcEqual $reset.script 'NO_RESULT' "$kind reset restores default"
            Assert-AcEqual $resetAgain.script $reset.script "$kind duplicate reset is idempotent"
            Assert-AcEqual $afterReset.script 'NO_RESULT' "$kind reset is observable by read"
            Assert-AcEqual $afterReset.consumed $false "$kind reset does not invent consumption"
        }
        $invalidId=Invoke-AcHttp POST '/api/reference-fixtures/settlement-executor-script' @{executionId='';script='SUCCESS'} @(400)
        $invalidScript=Invoke-AcHttp POST '/api/reference-fixtures/settlement-executor-script' @{executionId='EXEC-A9-INVALID';script='BOGUS'} @(400)
        Assert-AcEqual (Get-AcErrorCode $invalidId.Body) 'VALIDATION_ERROR' 'empty executionId has stable ApiError'
        Assert-AcEqual (Get-AcErrorCode $invalidScript.Body) 'VALIDATION_ERROR' 'unknown script has stable ApiError'
        $flow=New-SettlementFixture $c 'a9';$executionId='EXEC-A9'
        Configure-SettlementScript $executionId 'SUCCESS'|Out-Null
        $executed=(Execute-Settlement $c $flow.SettlementId $executionId 'A9-KEY').Body
        $firstRead=Get-SettlementScript $executionId;$firstDetail=Get-Settlement $flow.SettlementId
        $firstOperation=Get-Operation $executed.receipt.readAfter.operationUrl
        $reconfigured=Configure-SettlementScript $executionId 'UNKNOWN'
        $reset=Reset-SettlementScript $executionId;$resetAgain=Reset-SettlementScript $executionId
        $read=Get-SettlementScript $executionId
        $replay=(Execute-Settlement $c $flow.SettlementId $executionId 'A9-KEY').Body
        $after=Get-Settlement $flow.SettlementId;$afterOperation=Get-Operation $executed.receipt.readAfter.operationUrl
        foreach ($view in @($firstRead,$reconfigured,$reset,$resetAgain,$read)) {
            Assert-Ac -Condition ($view.consumed -and $view.script -eq 'SUCCESS' -and $view.observation -eq 'SUCCESS') -Message 'consumed script and observation remain frozen after configure/reset/read' -Actual $view
            Assert-AcEqual $view.diagnosticSummary $firstRead.diagnosticSummary 'consumed diagnostic remains frozen'
        }
        Assert-AcEqual $executed.executionId $executionId 'Execute consumed the configured identity'
        Assert-AcEqual $replay.receipt.operationId $executed.receipt.operationId 'old complete identity replays original Operation'
        Assert-AcEqual $replay.attemptId $executed.attemptId 'old complete identity replays original execution'
        Assert-AcEqual $replay.executorObservation 'SUCCESS' 'replay returns frozen first observation'
        Assert-AcEqual $after.attempts.Count 1 'reconfigure/reset/replay add no execution'
        Assert-AcEqual ($after.attempts[0] | ConvertTo-Json -Depth 30 -Compress) ($firstDetail.attempts[0] | ConvertTo-Json -Depth 30 -Compress) 'reconfigure/reset/replay preserve execution evidence'
        Assert-AcEqual $afterOperation.updatedAt $firstOperation.updatedAt 'replay does not mutate Operation'
        Add-AcObservation 'consumedScript' $read;Add-AcObservation 'settlement' $after
    }
    Run-Scenario 'A10' 'SUCCESS script is consumed once and replay has one effect' {
        $c=New-ScenarioContext 10;$flow=New-SettlementFixture $c 'a10';$id='EXEC-A10';Configure-SettlementScript $id 'SUCCESS'|Out-Null
        $first=(Execute-Settlement $c $flow.SettlementId $id 'A10-KEY').Body
        $before=Get-Settlement $flow.SettlementId;$scriptBefore=Get-SettlementScript $id
        $replay=(Execute-Settlement $c $flow.SettlementId $id 'A10-KEY').Body
        $detail=Get-Settlement $flow.SettlementId;$scriptAfter=Get-SettlementScript $id
        $notifications=Search-SettlementNotifications $c.MerchantId
        Assert-AcEqual $detail.status 'SETTLED' 'SUCCESS script settles the resource'
        Assert-AcEqual $detail.finality 'FINAL' 'settled result is final'
        Assert-AcEqual $detail.settledFactFormed $true 'SUCCESS forms settlement fact'
        Assert-AcEqual $before.completedAt $detail.completedAt 'replay does not form a second settled fact'
        Assert-AcEqual $replay.receipt.operationId $first.receipt.operationId 'execution replay returns the same Operation'
        Assert-AcEqual $replay.attemptId $first.attemptId 'execution replay retains attempt'
        Assert-AcEqual $detail.attempts.Count 1 'execution replay creates no second execution'
        Assert-AcEqual $detail.attempts[0].executionId $id 'detail exposes caller executionId'
        Assert-AcEqual $detail.attempts[0].receipts.Count 1 'SUCCESS executor has one accepted result receipt'
        Assert-AcEqual $detail.attempts[0].notificationReceiveCount 1 'replay sends no second result'
        Assert-AcEqual $scriptAfter.observation 'SUCCESS' 'public script read observes success'
        Assert-AcEqual $scriptAfter.consumed $true 'script was consumed'
        Assert-AcEqual ($scriptAfter | ConvertTo-Json -Depth 10 -Compress) ($scriptBefore | ConvertTo-Json -Depth 10 -Compress) 'replay does not consume script twice'
        Assert-AcEqual $notifications.items.Count 1 'settlement completion has one merchant notification intent'
        Add-AcObservation 'settlement' $detail;Add-AcObservation 'settlementNotifications' $notifications
    }
    Run-Scenario 'A11' 'FAILURE preserves diagnostics and permits one controlled new execution' {
        $c=New-ScenarioContext 11;$flow=New-SettlementFixture $c 'a11';$confirmed=Get-Settlement $flow.SettlementId
        Configure-SettlementScript 'EXEC-A11-1' 'FAILURE'|Out-Null
        $failed=(Execute-Settlement $c $flow.SettlementId 'EXEC-A11-1' 'A11-KEY-1').Body
        $failureDetail=Get-Settlement $flow.SettlementId;$failureScript=Get-SettlementScript 'EXEC-A11-1'
        $failureReplay=(Execute-Settlement $c $flow.SettlementId 'EXEC-A11-1' 'A11-KEY-1').Body
        $replayedDetail=Get-Settlement $flow.SettlementId;$replayedScript=Get-SettlementScript 'EXEC-A11-1'
        Assert-AcEqual $failed.status 'EXECUTION_FAILED' 'FAILURE script records explicit failure'
        Assert-AcEqual $failureReplay.receipt.operationId $failed.receipt.operationId 'FAILURE replay returns original Operation'
        Assert-AcEqual $failureReplay.attemptId $failed.attemptId 'FAILURE replay returns original attempt'
        Assert-AcEqual $replayedDetail.attempts.Count 1 'FAILURE replay adds no attempt'
        Assert-AcEqual $replayedDetail.attempts[0].receipts.Count 1 'FAILURE replay adds no diagnostic result receipt'
        Assert-AcEqual $replayedDetail.attempts[0].diagnosticSummary $failureDetail.attempts[0].diagnosticSummary 'FAILURE diagnostic is frozen'
        Assert-AcEqual ($replayedScript | ConvertTo-Json -Depth 10 -Compress) ($failureScript | ConvertTo-Json -Depth 10 -Compress) 'FAILURE script consumption is once'
        Configure-SettlementScript 'EXEC-A11-2' 'SUCCESS'|Out-Null
        $retry=(Execute-Settlement $c $flow.SettlementId 'EXEC-A11-2' 'A11-KEY-2').Body
        $detail=Get-Settlement $flow.SettlementId
        Assert-AcEqual $retry.status 'SETTLED' 'explicit failure permits controlled new identity'
        Assert-AcEqual $detail.attempts.Count 2 'failure retry retains immutable execution history'
        Assert-AcEqual $detail.attempts[0].executionId 'EXEC-A11-1' 'failure diagnostics remain on original identity'
        Assert-AcEqual $detail.attempts[0].receipts.Count 1 'original failure evidence remains singular'
        Assert-AcEqual $detail.attempts[1].executionId 'EXEC-A11-2' 'success uses controlled new identity'
        Assert-AcEqual $detail.attempts[1].executorObservation 'SUCCESS' 'new script supplies success observation'
        Assert-SettlementComposition $confirmed $failureDetail 'FAILURE execution'
        Assert-SettlementComposition $confirmed $detail 'SUCCESS retry'
        Add-AcObservation 'failedExecution' $failureDetail;Add-AcObservation 'retriedSettlement' $detail
    }
    Run-Scenario 'A12' 'UNKNOWN and NO_RESULT retain identity and forbid re-execution' {
        foreach ($case in @(
            @{ContextId='PAY-AC-912';Kind='UNKNOWN';ExecutionId='EXEC-A12-U';Key='A12-U'},
            @{ContextId='PAY-AC-928';Kind='NO_RESULT';ExecutionId='EXEC-A12-N';Key='A12-N'}
        )) {
            $c=Initialize-AcScenario $case.ContextId
            $flow=New-SettlementFixture $c $case.Kind.ToLowerInvariant()
            Configure-SettlementScript $case.ExecutionId $case.Kind|Out-Null
            $executed=(Execute-Settlement $c $flow.SettlementId $case.ExecutionId $case.Key).Body
            $scriptBefore=Get-SettlementScript $case.ExecutionId
            $before=Get-Settlement $flow.SettlementId
            $operation=Get-Operation $executed.receipt.readAfter.operationUrl
            Set-AcClock $c.BaseInstant.AddHours(14)|Out-Null
            Invoke-AcHttp POST '/api/reference-fixtures/maintenance' @{action='SETTLEMENT_UNKNOWN_REVIEW'} @(200)|Out-Null
            $reviewed=Get-Settlement $flow.SettlementId
            $reviews=(Invoke-AcHttp POST '/api/manual-reviews/search' @{merchantId=$c.MerchantId;relatedResourceId=$flow.SettlementId;pageSize=20} @(200)).Body
            $newId="$($case.ExecutionId)-NEW"
            Configure-SettlementScript $newId 'SUCCESS'|Out-Null
            $forbidden=Execute-Settlement $c $flow.SettlementId $newId "$($case.Key)-NEW" @(409)
            $void=Invoke-AcHttp POST "/api/merchant-settlements/$($flow.SettlementId)/voids" @{
                reason="unknown $($case.Kind) must remain frozen";evidence="evidence://$($case.Kind)/void"
                idempotencyKey="$($case.Key)-VOID";createReplacement=$false
            } @(409) 'fixture-settlement-operator'
            $replacement=Invoke-AcHttp POST "/api/merchant-settlements/$($flow.SettlementId)/voids" @{
                reason="unknown $($case.Kind) cannot be replaced";evidence="evidence://$($case.Kind)/replacement"
                idempotencyKey="$($case.Key)-REPLACEMENT";createReplacement=$true
            } @(409) 'fixture-settlement-operator'
            $after=Get-Settlement $flow.SettlementId
            $scriptAfter=Get-SettlementScript $case.ExecutionId;$unconsumed=Get-SettlementScript $newId
            $operationAfter=Get-Operation $executed.receipt.readAfter.operationUrl
            Assert-AcEqual $executed.executionId $case.ExecutionId "$($case.Kind) response carries caller identity"
            Assert-AcEqual $executed.executorObservation $case.Kind "$($case.Kind) executor observation is explicit"
            Assert-AcEqual $scriptBefore.observation $case.Kind "$($case.Kind) script was actually consumed"
            Assert-AcEqual $scriptAfter.consumed $true "$($case.Kind) script remains consumed"
            Assert-AcEqual $scriptAfter.observation $scriptBefore.observation "$($case.Kind) observation stays frozen"
            Assert-AcEqual $reviewed.status 'RESULT_UNKNOWN' "$($case.Kind) remains unknown"
            Assert-AcEqual $reviewed.finality 'REVIEW_REQUIRED' "$($case.Kind) reaches review threshold"
            Assert-AcEqual $reviewed.attempts[0].executionId $case.ExecutionId "$($case.Kind) review retains execution identity"
            Assert-Ac -Condition ($reviews.items.Count -ge 1 -and ($reviews|ConvertTo-Json -Depth 30 -Compress).Contains($case.ExecutionId)) -Message "$($case.Kind) review publicly references original executionId" -Actual $reviews
            foreach ($rejected in @($forbidden,$void,$replacement)) {
                Assert-AcEqual (Get-AcErrorCode $rejected.Body) 'RESULT_UNKNOWN_REEXECUTION_FORBIDDEN' "$($case.Kind) forbids new execution, void and replacement"
            }
            Assert-AcEqual $after.attempts.Count 1 "$($case.Kind) still has exactly one execution"
            Assert-AcEqual $after.settledFactFormed $false "$($case.Kind) produces no settled fact"
            Assert-AcEqual $after.replacementSettlementId $null "$($case.Kind) creates no replacement"
            Assert-AcEqual $after.attempts[0].executionId $case.ExecutionId "$($case.Kind) keeps original attempt"
            Assert-AcEqual $unconsumed.consumed $false "$($case.Kind) forbidden new identity does not consume SUCCESS script"
            Assert-AcEqual $operationAfter.updatedAt $operation.updatedAt "$($case.Kind) rejected commands do not mutate original Operation"
            Assert-AcEqual $after.netMoney.amountMinor $before.netMoney.amountMinor "$($case.Kind) forbids second funds effect"
            Add-AcObservation "$($case.Kind)Settlement" $after
            Add-AcObservation "$($case.Kind)Reviews" $reviews
        }
    }
    Run-Scenario 'A13' 'caller executionId crosses detail callback review and payment timeline' {
        $c=New-ScenarioContext 13;$flow=New-SettlementFixture $c 'a13';$id='EXEC-A13';Configure-SettlementScript $id 'UNKNOWN'|Out-Null;$execution=(Execute-Settlement $c $flow.SettlementId $id 'A13-KEY').Body;$before=(Invoke-AcHttp GET "/api/merchant-settlements/$($flow.SettlementId)" $null @(200)).Body;$amount=[decimal]$before.netMoney.amountMinor/100;$callback=Send-AcSettlementResult $c $flow.SettlementId $execution $amount 'UNKNOWN' 'a13-callback';Set-AcClock $c.BaseInstant.AddHours(20)|Out-Null;Invoke-AcHttp POST '/api/reference-fixtures/maintenance' @{action='SETTLEMENT_UNKNOWN_REVIEW'} @(200)|Out-Null
        $detail=(Invoke-AcHttp GET "/api/merchant-settlements/$($flow.SettlementId)" $null @(200)).Body;$reviews=(Invoke-AcHttp POST '/api/manual-reviews/search' @{merchantId=$c.MerchantId;relatedResourceId=$flow.SettlementId;pageSize=20} @(200)).Body;$timeline=(Invoke-AcHttp GET "/api/payments/$($flow.Payment.PaymentId)/timeline?pageSize=100" $null @(200)).Body
        $executionEvents=@($timeline.items|Where-Object {$_.eventType -eq 'SETTLEMENT_EXECUTION' -and ($_.refs.executionId -eq $id -or $_.payload.identity -eq $id)})
        $reviewJson=$reviews|ConvertTo-Json -Depth 30 -Compress
        Assert-AcEqual $detail.attempts[0].executionId $id 'settlement detail uses caller executionId';Assert-AcEqual $execution.executionId $id 'execute response uses caller executionId';Assert-AcEqual $callback.executionId $id 'result callback response uses caller executionId';Assert-Ac -Condition ($reviewJson.Contains($id)) -Message 'ManualReview references caller executionId' -Actual $reviews;Assert-Ac -Condition ($executionEvents.Count -ge 1) -Message 'payment timeline references caller executionId' -Actual $timeline
    }
    Run-Scenario 'A14' 'execution and idempotency rebinding conflicts are stable' {
        $c=New-ScenarioContext 14;$flow=New-SettlementFixture $c 'a14'
        $otherContext=Initialize-AcScenario 'PAY-AC-929'
        $second=New-SettlementFixture $otherContext 'a14-other-settlement'
        $id='EXEC-A14';$key='A14-KEY';$alternateId='EXEC-A14-OTHER'
        Configure-SettlementScript $id 'SUCCESS'|Out-Null
        Configure-SettlementScript $alternateId 'SUCCESS'|Out-Null
        $first=(Execute-Settlement $c $flow.SettlementId $id $key).Body
        $before=Get-Settlement $flow.SettlementId;$beforeOperation=Get-Operation $first.receipt.readAfter.operationUrl
        $replay=(Execute-Settlement $c $flow.SettlementId $id $key).Body
        Assert-AcEqual $replay.receipt.operationId $first.receipt.operationId 'complete binding replays original Operation'
        Assert-AcEqual $replay.attemptId $first.attemptId 'complete binding replays original execution'
        Assert-AcEqual $replay.receipt.acceptanceStatus 'ALREADY_ACCEPTED' 'complete binding is explicit replay'
        $bindings=@(
            @{Label='key changes executionId';SettlementId=$flow.SettlementId;MerchantId=$c.MerchantId;ExecutionId=$alternateId;Channel='C-001';Key=$key;Code='IDEMPOTENCY_CONFLICT'},
            @{Label='key changes channel';SettlementId=$flow.SettlementId;MerchantId=$c.MerchantId;ExecutionId=$id;Channel='C-002';Key=$key;Code='IDEMPOTENCY_CONFLICT'},
            @{Label='key changes settlement';SettlementId=$second.settlementId;MerchantId=$c.MerchantId;ExecutionId=$id;Channel='C-001';Key=$key;Code='IDEMPOTENCY_CONFLICT'},
            @{Label='key changes merchant and identity remains';SettlementId=$flow.SettlementId;MerchantId="$($c.MerchantId)-other";ExecutionId=$id;Channel='C-001';Key=$key;Code='SETTLEMENT_EXECUTION_IDENTITY_CONFLICT'},
            @{Label='identity changes key';SettlementId=$flow.SettlementId;MerchantId=$c.MerchantId;ExecutionId=$id;Channel='C-001';Key='A14-OTHER-KEY';Code='SETTLEMENT_EXECUTION_IDENTITY_CONFLICT'},
            @{Label='identity changes channel';SettlementId=$flow.SettlementId;MerchantId=$c.MerchantId;ExecutionId=$id;Channel='C-002';Key='A14-CHANNEL-KEY';Code='SETTLEMENT_EXECUTION_IDENTITY_CONFLICT'},
            @{Label='identity changes settlement';SettlementId=$second.settlementId;MerchantId=$c.MerchantId;ExecutionId=$id;Channel='C-001';Key='A14-SETTLEMENT-KEY';Code='SETTLEMENT_EXECUTION_IDENTITY_CONFLICT'},
            @{Label='identity changes merchant';SettlementId=$flow.SettlementId;MerchantId="$($c.MerchantId)-other";ExecutionId=$id;Channel='C-001';Key='A14-MERCHANT-KEY';Code='SETTLEMENT_EXECUTION_IDENTITY_CONFLICT'}
        )
        foreach ($binding in $bindings) {
            $error=Invoke-AcHttp POST "/api/merchant-settlements/$($binding.SettlementId)/executions" @{
                merchantId=$binding.MerchantId;executionId=$binding.ExecutionId
                executionChannelId=$binding.Channel;idempotencyKey=$binding.Key
            } @(409)
            Assert-AcEqual (Get-AcErrorCode $error.Body) $binding.Code $binding.Label
            if ($binding.Code -eq 'IDEMPOTENCY_CONFLICT') {
                Assert-AcEqual $error.Body.details.operationId $first.receipt.operationId "$($binding.Label) references original Operation"
            } else {
                Assert-AcEqual $error.Body.details.executionId $id "$($binding.Label) references caller identity"
                Assert-AcEqual $error.Body.details.settlementId $flow.SettlementId "$($binding.Label) references bound settlement"
            }
        }
        $detail=Get-Settlement $flow.SettlementId;$secondDetail=Get-Settlement $second.settlementId
        $script=Get-SettlementScript $id;$unusedScript=Get-SettlementScript $alternateId
        $operation=Get-Operation $first.receipt.readAfter.operationUrl
        Assert-AcEqual $detail.attempts.Count 1 'conflicts create no second execution on original settlement'
        Assert-AcEqual $secondDetail.attempts.Count 0 'conflicts create no execution on other settlement'
        Assert-AcEqual $detail.settledFactFormed $true 'original settled fact remains'
        Assert-AcEqual $detail.completedAt $before.completedAt 'conflicts do not repeat settlement completion'
        Assert-AcEqual ($detail.attempts[0] | ConvertTo-Json -Depth 30 -Compress) ($before.attempts[0] | ConvertTo-Json -Depth 30 -Compress) 'conflicts preserve original execution evidence'
        Assert-AcEqual $script.consumed $true 'original script remains consumed'
        Assert-AcEqual $script.observation 'SUCCESS' 'original script observation remains SUCCESS'
        Assert-AcEqual $unusedScript.consumed $false 'conflicts do not consume alternate execution script'
        Assert-AcEqual $operation.updatedAt $beforeOperation.updatedAt 'conflicts do not mutate original Operation'
        Add-AcObservation 'originalSettlement' $detail;Add-AcObservation 'otherSettlement' $secondDetail
    }
    Run-Scenario 'A15' 'clean H2 real HTTP aggregate verification' {
        $prior=@($script:results|Where-Object {$_.scenarioId -ne 'A15'})
        $scenarioEvidence=@($prior | ForEach-Object {
            [pscustomobject][ordered]@{
                scenarioId=$_.scenarioId
                title=$_.title
                status=$_.status
                requestCount=$_.requestCount
                responseCount=$_.responseCount
                assertionCount=$_.assertionCount
                evidenceFile=$_.evidenceFile
                normalizedDigest=$_.normalizedDigest
            }
        })
        Assert-AcEqual $prior.Count 14 'runner executed all fourteen directed capabilities'
        Assert-AcEqual (@($prior|Where-Object status -eq 'PASSED').Count) 14 'all directed capabilities passed through external HTTP'
        Assert-AcEqual (@($prior|Where-Object {$_.assertionCount -gt 0}).Count) 14 'all directed capabilities made non-zero assertions'
        Assert-AcEqual (@($prior|Where-Object {$_.requestCount -gt 0}).Count) 14 'all directed capabilities retain public HTTP requests'
        Assert-AcEqual (@($prior|Where-Object {$_.requestCount -eq $_.responseCount}).Count) 14 'every retained public HTTP request has a response'
        Assert-AcEqual (@($prior|Where-Object {Test-Path -LiteralPath (Join-Path $EvidenceRoot $_.evidenceFile)}).Count) 14 'all directed capability evidence files are retained'
        Assert-Ac -Condition ($metadata.service.pid -ne $metadata.service.clientPid) -Message 'external HTTP client is separate from Boot JVM' -Actual $metadata.service
        Assert-Ac -Condition ($metadata.service.databaseName -match '^authoritative_payment_http_' -and $metadata.service.databaseName -match $runId.Replace('-','_')) -Message 'Boot JVM uses a freshly named in-memory H2' -Actual $metadata.service.databaseName
        Assert-Ac -Condition ((Test-Path -LiteralPath $metadata.service.jarPath) -and (Test-Path -LiteralPath $metadata.service.stdout)) -Message 'independent Boot jar and service logs are retained' -Actual $metadata.service
        $coverageManifest=Get-CoverageManifest $prior
        Assert-AcEqual $coverageManifest.Count 82 'coverage manifest enumerates A1-A82 exactly once'
        Assert-AcEqual (@($coverageManifest.acceptanceId|Sort-Object -Unique).Count) 82 'coverage manifest acceptance IDs are unique'
        Assert-AcEqual (@($coverageManifest|Where-Object coverageKind -eq 'directed-real-http').Count) 15 'coverage manifest maps fifteen directed brief scenarios'
        Assert-AcEqual (@($coverageManifest|Where-Object coverageKind -eq 'shared-pay-ac-real-http').Count) 67 'coverage manifest maps all sixty-seven PAY-AC scenarios'
        Add-AcObservation 'scenarioEvidence' $scenarioEvidence
        Add-AcObservation 'coverageManifest' $coverageManifest
        Add-AcObservation 'service' $metadata.service
    }
} finally {
    Stop-ServiceProcess $service
}

$coverageManifest=Get-CoverageManifest $results
$summary=[ordered]@{schemaVersion=2;sourceCommit='3b66db675356e77c720081e0410baf444b7baa9c';runId=$runId;clientPid=$PID;database='clean in-memory H2';expectedScenarioCount=15;executedScenarioCount=$results.Count;passedScenarioCount=@($results|Where-Object status -eq 'PASSED').Count;failedScenarioCount=@($results|Where-Object status -eq 'FAILED').Count;results=$results;scenarioEvidence=@($results|ForEach-Object {[pscustomobject][ordered]@{scenarioId=$_.scenarioId;status=$_.status;requestCount=$_.requestCount;responseCount=$_.responseCount;assertionCount=$_.assertionCount;evidenceFile=$_.evidenceFile;normalizedDigest=$_.normalizedDigest}});coverageManifest=$coverageManifest;generatedAt=[DateTimeOffset]::UtcNow.ToString('o')}
Write-Evidence (Join-Path $EvidenceRoot 'summary.json') $summary
Write-Host "Evidence: $EvidenceRoot"
Write-Host "A1-A15: $($summary.passedScenarioCount)/$($summary.executedScenarioCount) passed"
if ($summary.executedScenarioCount -ne 15 -or $summary.failedScenarioCount -gt 0 -or @($results|Where-Object {$_.status -eq 'PASSED' -and $_.assertionCount -eq 0}).Count -gt 0) { exit 1 }
exit 0
