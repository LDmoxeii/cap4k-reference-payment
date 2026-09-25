Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:BaseUrl = $null
$script:RunMetadata = $null
$script:HttpTrace = @()
$script:Assertions = @()
$script:Observations = @()
$script:Scenario = $null

function Set-PayAcHttpRuntime {
    param([Parameter(Mandatory)][string]$BaseUrl, [Parameter(Mandatory)]$RunMetadata)
    $script:BaseUrl = $BaseUrl.TrimEnd('/')
    $script:RunMetadata = $RunMetadata
}

function ConvertTo-AcJson {
    param($Value, [int]$Depth = 30)
    return ($Value | ConvertTo-Json -Depth $Depth -Compress)
}

function Invoke-AcHttp {
    param(
        [Parameter(Mandatory)][ValidateSet('GET','POST','PUT','DELETE')][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        $Body = $null,
        [int[]]$ExpectedStatus = @(200),
        [string]$ActorAlias = $null,
        [hashtable]$ExtraHeaders = @{}
    )
    if (-not $script:BaseUrl) { throw 'PAY-AC HTTP runtime has not been configured' }
    $headers = @{'Accept'='application/json'}
    if ($ActorAlias) { $headers['X-Reference-Actor-Context'] = $ActorAlias }
    foreach ($entry in $ExtraHeaders.GetEnumerator()) { $headers[$entry.Key] = $entry.Value }
    $uri = "$($script:BaseUrl)$Path"
    $params = @{
        Uri = $uri
        Method = $Method
        Headers = $headers
        SkipHttpErrorCheck = $true
        TimeoutSec = 30
    }
    $requestBody = $null
    if ($null -ne $Body) {
        $requestBody = ConvertTo-AcJson $Body
        $params['ContentType'] = 'application/json; charset=utf-8'
        $params['Body'] = $requestBody
    }
    $response = Invoke-WebRequest @params
    $parsed = $null
    if (-not [string]::IsNullOrWhiteSpace($response.Content)) {
        try { $parsed = $response.Content | ConvertFrom-Json -Depth 50 } catch { $parsed = $response.Content }
    }
    $traceItem = [ordered]@{
        sequence = $script:HttpTrace.Count + 1
        method = $Method
        path = $Path
        requestHeaders = if ($ActorAlias) { @{ 'X-Reference-Actor-Context' = $ActorAlias } } else { @{} }
        requestBody = $Body
        responseStatus = [int]$response.StatusCode
        responseBody = $parsed
    }
    $script:HttpTrace += [pscustomobject]$traceItem
    if ($ExpectedStatus -notcontains [int]$response.StatusCode) {
        throw "HTTP $Method $Path returned $($response.StatusCode), expected $($ExpectedStatus -join ','): $($response.Content)"
    }
    return [pscustomobject]@{ StatusCode = [int]$response.StatusCode; Body = $parsed; Text = $response.Content }
}

function Assert-Ac {
    param([Parameter(Mandatory)][bool]$Condition, [Parameter(Mandatory)][string]$Message, $Actual = $null)
    $record = [ordered]@{ assertion = $Message; passed = $Condition; actual = $Actual }
    $script:Assertions += [pscustomobject]$record
    if (-not $Condition) { throw "Assertion failed: $Message; actual=$(ConvertTo-AcJson $Actual)" }
}

function Assert-AcEqual {
    param($Actual, $Expected, [Parameter(Mandatory)][string]$Message)
    Assert-Ac -Condition ($Actual -eq $Expected) -Message $Message -Actual @{ expected = $Expected; actual = $Actual }
}

function Add-AcObservation {
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)]$Value)
    $script:Observations += [pscustomobject]@{ name = $Name; value = $Value }
}

function Add-AcExternalHttpTrace {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        $RequestBody,
        [Parameter(Mandatory)][int]$ResponseStatus,
        $ResponseBody,
        [Parameter(Mandatory)][int]$ClientPid
    )
    $script:HttpTrace += [pscustomobject][ordered]@{
        sequence = $script:HttpTrace.Count + 1
        method = $Method
        path = $Path
        requestHeaders = @{}
        requestBody = $RequestBody
        responseStatus = $ResponseStatus
        responseBody = $ResponseBody
        clientPid = $ClientPid
    }
}

function Get-AcBaseUrl { return $script:BaseUrl }

function Get-AcErrorCode {
    param($ResponseBody)
    if ($null -eq $ResponseBody) { return $null }
    $properties = @($ResponseBody.PSObject.Properties.Name)
    if ($properties -notcontains 'code') { return $null }
    Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace([string]$ResponseBody.message)) -Message 'ApiError has a non-empty message' -Actual $ResponseBody
    Assert-Ac -Condition ($properties -contains 'details') -Message 'ApiError exposes top-level details' -Actual $ResponseBody
    Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace([string]$ResponseBody.correlationId)) -Message 'ApiError exposes correlationId' -Actual $ResponseBody
    Assert-Ac -Condition (($properties -contains 'retryable') -and ($ResponseBody.retryable -is [bool])) -Message 'ApiError exposes boolean retryable' -Actual $ResponseBody
    Assert-Ac -Condition ($properties -notcontains 'receipt') -Message 'synchronous ApiError has no OperationReceipt' -Actual $ResponseBody
    return [string]$ResponseBody.code
}

function ConvertTo-MinorString {
    param([Parameter(Mandatory)][decimal]$Amount)
    return ([decimal]::Round($Amount * 100, 0, [MidpointRounding]::AwayFromZero)).ToString('0', [Globalization.CultureInfo]::InvariantCulture)
}

function Initialize-AcScenario {
    param([Parameter(Mandatory)][string]$ScenarioId)
    $number = [int]$ScenarioId.Substring($ScenarioId.Length - 3)
    $day = [datetime]::SpecifyKind([datetime]'2026-01-01T00:00:00', [DateTimeKind]::Utc).AddDays($number * 2)
    $instant = [DateTimeOffset]$day
    $merchant = "HTTP-$($ScenarioId.Replace('PAY-AC-','M-'))"
    $alias = $ScenarioId.ToLowerInvariant()
    Invoke-AcHttp POST '/api/reference-fixtures/policy/reset' @{} @(200) | Out-Null
    Invoke-AcHttp POST '/api/reference-fixtures/clock/set' @{ instant = $instant.ToString('o') } @(200) | Out-Null
    Invoke-AcHttp POST '/api/reference-fixtures/payment-channel-script/reset' @{ channelId = 'C-001' } @(200) | Out-Null
    Invoke-AcHttp POST '/api/reference-fixtures/merchant-notification-sender-script/clear' @{} @(200) | Out-Null
    $channel = Invoke-AcHttp POST '/api/reference-fixtures/merchant-channels' @{
        merchantId = $merchant; channelId = 'C-001'; currency = 'CNY'; paymentMethod = 'CARD'; status = 'ACTIVE'
        minimumAmount = 0.01; maximumAmount = 1000000.00; routingPriority = 10; refundWindowDays = 30
        refundResultReviewAfterMinutes = 5; settlementFeeBasisPoints = 60
        settlementFixedFeeAmount = 0; settlementFeeRoundingMode = 'HALF_UP'; settlementResultReviewAfterMinutes = 5
    } @(200)
    $localDate = [TimeZoneInfo]::ConvertTime($instant, [TimeZoneInfo]::FindSystemTimeZoneById('China Standard Time')).Date.ToString('yyyy-MM-dd')
    return [pscustomobject]@{
        ScenarioId = $ScenarioId; Alias = $alias; MerchantId = $merchant; ChannelId = 'C-001'
        BaseInstant = $instant; BusinessDate = $localDate; ChannelConfiguration = $channel.Body
    }
}

function Set-AcClock {
    param([Parameter(Mandatory)][DateTimeOffset]$Instant)
    return (Invoke-AcHttp POST '/api/reference-fixtures/clock/set' @{ instant = $Instant.ToString('o') } @(200)).Body
}

function Advance-AcClock {
    param([Parameter(Mandatory)][string]$Duration)
    return (Invoke-AcHttp POST '/api/reference-fixtures/clock/advance' @{ duration = $Duration } @(200)).Body
}

function New-AcPayment {
    param(
        [Parameter(Mandatory)]$Context,
        [decimal]$Amount = 100.00,
        [string]$Currency = 'CNY',
        [string]$OrderSuffix = 'order',
        [string]$KeySuffix = 'create',
        [Nullable[DateTimeOffset]]$ExpiresAt = $null
    )
    if ($null -ne $ExpiresAt) {
        $clock = (Invoke-AcHttp GET '/api/reference-fixtures/clock' $null @(200)).Body
        $duration = ([DateTimeOffset]$ExpiresAt) - ([DateTimeOffset]$clock.instant)
        if ($duration -le [TimeSpan]::Zero) { throw 'payment expiry override must be in the future' }
        $isoDuration = [System.Xml.XmlConvert]::ToString($duration)
        Invoke-AcHttp POST '/api/reference-fixtures/policy' @{paymentExpiry=$isoDuration} @(200) | Out-Null
    }
    $response = Invoke-AcHttp POST '/api/payments' @{
        merchantId = $Context.MerchantId
        merchantOrderNumber = "$($Context.Alias)-$OrderSuffix"
        idempotencyKey = "$($Context.Alias)-$KeySuffix"
        money = @{ currency = $Currency; amountMinor = ConvertTo-MinorString $Amount }
        paymentMethod = 'CARD'
    } @(201)
    return $response.Body
}

function New-AcPaymentAttempt {
    param([Parameter(Mandatory)]$Context, [Parameter(Mandatory)][string]$PaymentId, [string]$Suffix = '1', [string]$RiskReason = $null)
    $body = @{ idempotencyKey = "$($Context.Alias)-payment-attempt-$Suffix" }
    if ($RiskReason) { $body['riskReason'] = $RiskReason }
    return (Invoke-AcHttp POST "/api/payments/$PaymentId/attempts" $body @(201)).Body
}

function Submit-AcPaymentAttempt {
    param([Parameter(Mandatory)]$Context, [Parameter(Mandatory)][string]$PaymentId, [Parameter(Mandatory)][string]$AttemptId, [string]$Suffix = '1')
    return (Invoke-AcHttp POST "/api/payments/$PaymentId/attempts/$AttemptId/submissions" @{
        idempotencyKey = "$($Context.Alias)-payment-submit-$Suffix"
    } @(200)).Body
}

function Register-AcCallbackEvidence {
    param(
        [Parameter(Mandatory)]$Context, [Parameter(Mandatory)][string]$Kind,
        [Parameter(Mandatory)][string]$ExternalIdentity, [Parameter(Mandatory)][string]$AssociationIdentity,
        [Parameter(Mandatory)][decimal]$Amount, [Parameter(Mandatory)][string]$RawPayload
    )
    return (Invoke-AcHttp POST '/api/reference-fixtures/callback-evidence' @{
        idempotencyKey = "$($Context.Alias)-evidence-$Kind-$ExternalIdentity"
        kind = $Kind; channelId = 'C-001'; externalIdentity = $ExternalIdentity
        associationIdentity = $AssociationIdentity
        money = @{ currency = 'CNY'; amountMinor = ConvertTo-MinorString $Amount }
        rawPayload = $RawPayload
    } @(201)).Body
}

function Send-AcPaymentResult {
    param(
        [Parameter(Mandatory)]$Context, [Parameter(Mandatory)][string]$PaymentId,
        [Parameter(Mandatory)][string]$AttemptId, [decimal]$Amount = 100.00,
        [string]$Result = 'SUCCESS', [string]$Suffix = '1', [bool]$Trusted = $true,
        [string]$TransactionId = $null, [DateTimeOffset]$OccurredAt = $Context.BaseInstant.AddMinutes(2)
    )
    if (-not $TransactionId) { $TransactionId = "CT-$($Context.Alias)-$Suffix" }
    $notificationId = "N-$($Context.Alias)-$Suffix-$Result"
    $rawPayload = "reference-$($Context.Alias)-$Suffix-$Result"
    if ($Trusted) {
        Register-AcCallbackEvidence $Context 'PAYMENT' $notificationId "$PaymentId|$AttemptId|$TransactionId" $Amount $rawPayload | Out-Null
    }
    $occurredAt = $OccurredAt.ToString('o')
    $response = Invoke-AcHttp POST '/api/channel/payment-results' @{
        channelId = 'C-001'; notificationId = $notificationId; paymentId = $PaymentId
        paymentAttemptId = $AttemptId; channelTransactionId = $TransactionId
        money = @{ currency = 'CNY'; amountMinor = (ConvertTo-MinorString ([decimal]$Amount)) }
        result = $Result; occurredAt = $occurredAt; rawPayload = $rawPayload
    } @(200)
    return [pscustomobject]@{ Body = $response.Body; NotificationId = $notificationId; TransactionId = $TransactionId; RawPayload = $rawPayload; OccurredAt = $occurredAt }
}

function New-AcSucceededPayment {
    param([Parameter(Mandatory)]$Context, [decimal]$Amount = 100.00, [string]$Suffix = 'main')
    $created = New-AcPayment $Context $Amount 'CNY' "order-$Suffix" "create-$Suffix"
    $attempt = New-AcPaymentAttempt $Context $created.paymentId $Suffix
    $submission = Submit-AcPaymentAttempt $Context $created.paymentId $attempt.paymentAttemptId $Suffix
    $result = Send-AcPaymentResult $Context $created.paymentId $attempt.paymentAttemptId $Amount 'SUCCESS' $Suffix $true
    $detail = (Invoke-AcHttp GET "/api/payments/$($created.paymentId)" $null @(200)).Body
    return [pscustomobject]@{ Created = $created; Attempt = $attempt; Submission = $submission; Result = $result; Detail = $detail; PaymentId = $created.paymentId; TransactionId = $result.TransactionId; OccurredAt = $result.OccurredAt }
}

function New-AcRefundRequest {
    param([Parameter(Mandatory)]$Context, [Parameter(Mandatory)][string]$PaymentId, [decimal]$Amount, [string]$Suffix = '1')
    return (Invoke-AcHttp POST '/api/refunds' @{
        merchantId = $Context.MerchantId; idempotencyKey = "$($Context.Alias)-refund-request-$Suffix"
        merchantRefundNo = "$($Context.Alias)-refund-no-$Suffix"; paymentId = $PaymentId
        money = @{ currency = 'CNY'; amountMinor = ConvertTo-MinorString $Amount }; reason = "reference HTTP refund $Suffix"
    } @(201)).Body
}

function New-AcRefundAttempt {
    param([Parameter(Mandatory)]$Context, [Parameter(Mandatory)][string]$RefundId, [string]$Suffix = '1')
    return (Invoke-AcHttp POST "/api/refunds/$RefundId/attempts" @{
        idempotencyKey = "$($Context.Alias)-refund-attempt-$Suffix"
    } @(201)).Body
}

function Submit-AcRefundAttempt {
    param([Parameter(Mandatory)]$Context, [Parameter(Mandatory)][string]$RefundId, [Parameter(Mandatory)][string]$AttemptId, [string]$Suffix = '1')
    return (Invoke-AcHttp POST "/api/refunds/$RefundId/attempts/$AttemptId/submissions" @{
        idempotencyKey = "$($Context.Alias)-refund-submit-$Suffix"
    } @(200)).Body
}

function Send-AcRefundResult {
    param(
        [Parameter(Mandatory)]$Context, [Parameter(Mandatory)][string]$RefundId,
        [Parameter(Mandatory)][string]$AttemptId, [Parameter(Mandatory)][string]$ChannelRefundId,
        [decimal]$Amount, [string]$Result = 'SUCCESS', [string]$Suffix = '1', [bool]$Trusted = $true
    )
    $notificationId = "RN-$($Context.Alias)-$Suffix-$Result"
    $rawPayload = "reference-refund-$($Context.Alias)-$Suffix-$Result"
    if ($Trusted) {
        Register-AcCallbackEvidence $Context 'REFUND' $notificationId "$RefundId|$AttemptId|$ChannelRefundId" $Amount $rawPayload | Out-Null
    }
    $response = Invoke-AcHttp POST '/api/channel/refund-results' @{
        channelId = 'C-001'; notificationId = $notificationId; refundId = $RefundId
        refundAttemptId = $AttemptId; channelRefundId = $ChannelRefundId
        money = @{ currency = 'CNY'; amountMinor = (ConvertTo-MinorString ([decimal]$Amount)) }
        result = $Result; occurredAt = $Context.BaseInstant.AddMinutes(5).ToString('o'); rawPayload = $rawPayload
    } @(200)
    return [pscustomobject]@{ Body = $response.Body; NotificationId = $notificationId }
}

function New-AcSucceededRefund {
    param([Parameter(Mandatory)]$Context, [Parameter(Mandatory)][string]$PaymentId, [decimal]$Amount, [string]$Suffix = '1')
    Set-AcClock $Context.BaseInstant.AddMinutes(3) | Out-Null
    $refund = New-AcRefundRequest $Context $PaymentId $Amount $Suffix
    $attempt = New-AcRefundAttempt $Context $refund.refundId $Suffix
    Submit-AcRefundAttempt $Context $refund.refundId $attempt.refundAttemptId $Suffix | Out-Null
    $before = (Invoke-AcHttp GET "/api/refunds/$($refund.refundId)" $null @(200)).Body
    $channelRefundId = [string]$before.attempts[0].channelRefundId
    $result = Send-AcRefundResult $Context $refund.refundId $attempt.refundAttemptId $channelRefundId $Amount 'SUCCESS' $Suffix $true
    $detail = (Invoke-AcHttp GET "/api/refunds/$($refund.refundId)" $null @(200)).Body
    $payment = (Invoke-AcHttp GET "/api/payments/$PaymentId" $null @(200)).Body
    return [pscustomobject]@{ Refund = $refund; Attempt = $attempt; Result = $result; Detail = $detail; Payment = $payment; RefundId = $refund.refundId; ChannelRefundId = $channelRefundId; OccurredAt = $Context.BaseInstant.AddMinutes(5).ToString('o') }
}

function New-AcBillRecord {
    param([string]$Identity, [string]$Kind, [string]$TransactionId, [decimal]$Amount, [string]$OccurredAt, [string]$Status = 'SUCCEEDED')
    $normalizedOccurredAt = ([DateTimeOffset]::Parse([string]$OccurredAt)).ToString('o')
    return @{
        recordIdentity = $Identity; channelTransactionIdentity = $TransactionId; transactionKind = $Kind
        money = @{ currency = 'CNY'; amountMinor = ConvertTo-MinorString $Amount }
        rawStatus = $Status; occurredAt = $normalizedOccurredAt
        receivedAt = ([DateTimeOffset]::Parse($normalizedOccurredAt)).AddSeconds(30).ToString('o'); rawEvidence = "evidence://$Identity"
    }
}

function Register-AcBillAndSignal {
    param(
        [Parameter(Mandatory)]$Context, [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Records,
        [string]$Revision = '1', [string]$Suffix = 'main', [int]$UnavailableReadCount = 0
    )
    $billIdentity = "BILL-$($Context.Alias)-$Suffix"
    $published = $Context.BaseInstant.AddHours(12).ToString('o')
    $registered = (Invoke-AcHttp POST '/api/reference-fixtures/bills' @{
        channelId = 'C-001'; billIdentity = $billIdentity; businessDate = $Context.BusinessDate
        currency = 'CNY'; businessTimezone = 'Asia/Shanghai'; revision = $Revision; completeness = 'COMPLETE'
        rawEvidence = "evidence://$billIdentity/$Revision"; payloadFingerprint = "$billIdentity-$Revision-fingerprint"
        publishedAt = $published; records = $Records; unavailableReadCount = $UnavailableReadCount
    } @(200)).Body
    $signal = (Invoke-AcHttp POST "/api/reference-fixtures/bills/$billIdentity/signals" @{
        channelId = 'C-001'; businessDate = $Context.BusinessDate; currency = 'CNY'; businessTimezone = 'Asia/Shanghai'
        signalIdentity = "SIGNAL-$billIdentity-$Revision"; announcedRevision = $Revision; publishedAt = $published
    } @(200)).Body
    $run = $null
    if ($signal.runId) { $run = (Invoke-AcHttp GET "/api/reconciliation-runs/$($signal.runId)" $null @(200)).Body }
    return [pscustomobject]@{ Registered = $registered; Signal = $signal; Run = $run; BillIdentity = $billIdentity; Revision = $Revision }
}

function Get-AcSettlementPeriod {
    param([Parameter(Mandatory)][string]$BusinessDate)
    $start = [DateTimeOffset]::Parse("${BusinessDate}T00:00:00+08:00")
    return @{ start = $start.ToUniversalTime().ToString('o'); end = $start.AddDays(1).ToUniversalTime().ToString('o'); timezone = 'Asia/Shanghai' }
}

function New-AcPreparedSettlement {
    param([Parameter(Mandatory)]$Context, [string]$Suffix = 'main', $Period = $null)
    if ($null -eq $Period) { $Period = Get-AcSettlementPeriod $Context.BusinessDate }
    return (Invoke-AcHttp POST '/api/merchant-settlements' @{
        merchantId = $Context.MerchantId; currency = 'CNY'; settlementPeriod = $Period
        idempotencyKey = "$($Context.Alias)-settlement-prepare-$Suffix"
    } @(201)).Body
}

function Confirm-AcSettlement {
    param([Parameter(Mandatory)]$Context, [Parameter(Mandatory)][string]$SettlementId, [string]$Suffix = 'main')
    return (Invoke-AcHttp POST "/api/merchant-settlements/$SettlementId/confirmations" @{
        idempotencyKey = "$($Context.Alias)-settlement-confirm-$Suffix"; reason = "reference confirmation $Suffix"; evidence = "evidence://settlement/$Suffix"
    } @(200) 'fixture-settlement-operator').Body
}

function Start-AcSettlementExecution {
    param([Parameter(Mandatory)]$Context, [Parameter(Mandatory)][string]$SettlementId, [string]$Suffix = 'main')
    return (Invoke-AcHttp POST "/api/merchant-settlements/$SettlementId/executions" @{
        executionChannelId = 'C-001'; idempotencyKey = "$($Context.Alias)-settlement-execute-$Suffix"
    } @(200)).Body
}

function Send-AcSettlementResult {
    param([Parameter(Mandatory)]$Context, [Parameter(Mandatory)][string]$SettlementId, [Parameter(Mandatory)]$Execution, [decimal]$Amount, [string]$Result = 'SUCCESS', [string]$Suffix = 'main')
    $externalIdentity = "STL-$($Execution.requestIdentity)"
    $notificationId = "SN-$($Context.Alias)-$Suffix-$Result"
    $rawPayload = "reference-settlement-$($Context.Alias)-$Suffix-$Result"
    $association = "$SettlementId|$($Execution.attemptId)|$($Execution.executionGroupIdentity)|$($Execution.requestIdentity)|$externalIdentity"
    Register-AcCallbackEvidence $Context 'SETTLEMENT' $notificationId $association $Amount $rawPayload | Out-Null
    return (Invoke-AcHttp POST '/api/channel/settlement-results' @{
        channelId = 'C-001'; notificationId = $notificationId; settlementId = $SettlementId
        executionAttemptId = $Execution.attemptId; executionGroupIdentity = $Execution.executionGroupIdentity
        requestIdentity = $Execution.requestIdentity; externalSettlementIdentity = $externalIdentity
        money = @{ currency = 'CNY'; amountMinor = ConvertTo-MinorString $Amount }
        result = $Result; resultCode = $Result
        occurredAt = $Context.BaseInstant.AddHours(13).ToString('o'); receivedAt = $Context.BaseInstant.AddHours(13).AddSeconds(30).ToString('o')
        rawPayload = $rawPayload
    } @(200)).Body
}

function New-AcReconciledPayment {
    param([Parameter(Mandatory)]$Context, [decimal]$Amount = 100.00, [string]$Suffix = 'main')
    $payment = New-AcSucceededPayment $Context $Amount $Suffix
    $record = New-AcBillRecord "$($Context.Alias)-record-$Suffix" 'PAYMENT' $payment.TransactionId $Amount $payment.OccurredAt
    $bill = Register-AcBillAndSignal $Context @($record) '1' $Suffix
    return [pscustomobject]@{ Payment = $payment; Bill = $bill }
}

function New-AcExecutableSettlement {
    param([Parameter(Mandatory)]$Context, [decimal]$Amount = 100.00, [string]$Suffix = 'main')
    $reconciled = New-AcReconciledPayment $Context $Amount $Suffix
    $prepared = New-AcPreparedSettlement $Context $Suffix
    $confirmed = Confirm-AcSettlement $Context $prepared.settlementId $Suffix
    $execution = Start-AcSettlementExecution $Context $prepared.settlementId $Suffix
    $detail = (Invoke-AcHttp GET "/api/merchant-settlements/$($prepared.settlementId)" $null @(200)).Body
    return [pscustomobject]@{ Reconciled = $reconciled; Prepared = $prepared; Confirmed = $confirmed; Execution = $execution; Detail = $detail }
}

function Get-AcNormalizedDigest {
    param([Parameter(Mandatory)]$Value)
    $json = ConvertTo-AcJson $Value 50
    $bytes = [Text.Encoding]::UTF8.GetBytes($json)
    $hash = [Security.Cryptography.SHA256]::HashData($bytes)
    return ([Convert]::ToHexString($hash)).ToLowerInvariant()
}

function Start-AcEvidenceScope {
    param($Scenario)
    $script:Scenario = $Scenario
    $script:HttpTrace = @()
    $script:Assertions = @()
    $script:Observations = @()
}

function Complete-AcEvidenceScope {
    param([string]$Status, [string]$Failure = $null)
    $assertionCount = @($script:Assertions).Count
    if ($Status -eq 'PASSED' -and $assertionCount -eq 0) {
        throw "Scenario $($script:Scenario.id) cannot be PASSED without at least one recorded assertion"
    }
    $normalized = [ordered]@{
        scenarioId = $script:Scenario.id
        assertions = @($script:Assertions | ForEach-Object { @{ assertion = $_.assertion; passed = $_.passed; actual = $_.actual } })
        observations = @($script:Observations)
    }
    return [ordered]@{
        schemaVersion = 1
        sourceCommit = '3b66db675356e77c720081e0410baf444b7baa9c'
        runId = $script:RunMetadata.runId
        scenarioId = $script:Scenario.id
        title = $script:Scenario.title
        fixture = $script:Scenario.fixture
        automationTestId = $script:Scenario.automationTestId
        httpScenarioId = "HTTP-$($script:Scenario.id)"
        status = $Status
        failure = $Failure
        service = $script:RunMetadata.service
        clientPid = $PID
        executedAt = [DateTimeOffset]::UtcNow.ToString('o')
        http = @($script:HttpTrace)
        publicQueryObservations = @($script:Observations)
        assertions = @($script:Assertions)
        assertionCount = $assertionCount
        normalizedDigest = Get-AcNormalizedDigest $normalized
    }
}

Export-ModuleMember -Function Set-PayAcHttpRuntime,Start-AcEvidenceScope,Complete-AcEvidenceScope,Initialize-AcScenario,Invoke-AcHttp,Assert-Ac,Assert-AcEqual,Add-AcObservation,Add-AcExternalHttpTrace,Get-AcBaseUrl,Get-AcErrorCode,New-AcPayment,New-AcPaymentAttempt,Submit-AcPaymentAttempt,Send-AcPaymentResult,New-AcSucceededPayment,New-AcRefundRequest,New-AcRefundAttempt,Submit-AcRefundAttempt,Send-AcRefundResult,New-AcSucceededRefund,New-AcBillRecord,Register-AcBillAndSignal,Get-AcSettlementPeriod,New-AcPreparedSettlement,Confirm-AcSettlement,Start-AcSettlementExecution,Send-AcSettlementResult,New-AcReconciledPayment,New-AcExecutableSettlement,Set-AcClock,Advance-AcClock,ConvertTo-MinorString,Register-AcCallbackEvidence,Get-AcNormalizedDigest
