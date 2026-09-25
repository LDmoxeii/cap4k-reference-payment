Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
function Get-PaymentDetail([string]$PaymentId) { return (Invoke-AcHttp GET "/api/payments/$PaymentId" $null @(200)).Body }
function Get-RefundDetail([string]$RefundId) { return (Invoke-AcHttp GET "/api/refunds/$RefundId" $null @(200)).Body }
function Get-SettlementDetail([string]$SettlementId) { return (Invoke-AcHttp GET "/api/merchant-settlements/$SettlementId" $null @(200)).Body }
function Search-ManualReviews($Body) { return (Invoke-AcHttp POST '/api/manual-reviews/search' $Body @(200)).Body }
function Search-Notifications($Body) { return (Invoke-AcHttp POST '/api/merchant-notifications/search' $Body @(200)).Body }
function Get-AcRawSha256([string]$Value) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
    return ([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))).ToLowerInvariant()
}

function Get-AcForgedCursor([string]$Cursor) {
    if ([string]::IsNullOrWhiteSpace($Cursor)) { throw 'cursor is required for tamper test' }
    $parts = $Cursor.Split('.')
    if ($parts.Count -ne 2 -or $parts[1].Length -lt 2) { throw 'signed cursor format is required for tamper test' }
    $replacement = if ($parts[1][0] -eq 'A') { 'B' } else { 'A' }
    return $parts[0] + '.' + $replacement + $parts[1].Substring(1)
}

function New-AcChildContext {
    param($Parent, [string]$Suffix, [int]$DayOffset = 0)
    $merchant = "$($Parent.MerchantId)-$Suffix"
    Invoke-AcHttp POST '/api/reference-fixtures/merchant-channels' @{
        merchantId=$merchant;channelId='C-001';currency='CNY';paymentMethod='CARD';status='ACTIVE';minimumAmount=0.01;maximumAmount=1000000.00;routingPriority=10
        refundWindowDays=30;refundResultReviewAfterMinutes=5;settlementFeeBasisPoints=60;settlementFixedFeeAmount=0;settlementFeeRoundingMode='HALF_UP';settlementResultReviewAfterMinutes=5
    } @(200) | Out-Null
    $base = $Parent.BaseInstant.AddDays($DayOffset)
    $localDate = [TimeZoneInfo]::ConvertTime($base, [TimeZoneInfo]::FindSystemTimeZoneById('China Standard Time')).Date.ToString('yyyy-MM-dd')
    return [pscustomobject]@{ScenarioId=$Parent.ScenarioId;Alias="$($Parent.Alias)-$Suffix";MerchantId=$merchant;ChannelId='C-001';BaseInstant=$base;BusinessDate=$localDate;ChannelConfiguration=$null}
}

function Wait-AcNotification {
    param($Filter, [int]$MinimumCount = 1)
    $latest = $null
    1..40 | ForEach-Object {
        if ($null -ne $latest -and $latest.items.Count -ge $MinimumCount) { return }
        $latest = Search-Notifications $Filter
        if ($latest.items.Count -lt $MinimumCount) { Start-Sleep -Milliseconds 100 }
    }
    return $latest
}

function Invoke-PaymentPayAcScenario {
    param([string]$Id, $Context)
    switch ($Id) {
        'PAY-AC-001' {
            $created = New-AcPayment $Context 100 'CNY' 'o001' 'k001'
            $detail = Get-PaymentDetail $created.paymentId
            Assert-AcEqual $created.status 'PAYABLE' 'new payment response is PAYABLE'
            Assert-AcEqual $detail.status 'PAYABLE' 'new payment authoritative query is PAYABLE'
            Assert-AcEqual $detail.money.currency 'CNY' 'payment Money currency is CNY'
            Assert-AcEqual $detail.money.amountMinor '10000' 'payment amount is CNY 100.00'
            Assert-Ac -Condition (-not [bool]$detail.successFactFormed) -Message 'creation does not form a paid fact' -Actual $detail.successFactFormed
            Add-AcObservation 'payment' $detail
        }
        'PAY-AC-002' {
            $first = New-AcPayment $Context 100 'CNY' 'o001' 'k001'
            $second = New-AcPayment $Context 100 'CNY' 'o001' 'k001'
            $listed = (Invoke-AcHttp POST '/api/payments/search' @{ merchantId=$Context.MerchantId; merchantOrderId="$($Context.Alias)-o001"; pageSize=10 } @(200)).Body
            Assert-AcEqual $second.paymentId $first.paymentId 'same create payload returns original paymentId'
            Assert-AcEqual $second.receipt.operationId $first.receipt.operationId 'same create payload returns original operationId'
            Assert-AcEqual $second.receipt.acceptanceStatus 'ALREADY_ACCEPTED' 'replay receipt is ALREADY_ACCEPTED'
            Assert-AcEqual $listed.items.Count 1 'replay creates no second payment'
            Add-AcObservation 'paymentList' $listed
        }
        'PAY-AC-003' {
            $first = New-AcPayment $Context 100 'CNY' 'o001' 'k001'
            $conflict = Invoke-AcHttp POST '/api/payments' @{
                merchantId=$Context.MerchantId; merchantOrderNumber="$($Context.Alias)-o001"; idempotencyKey="$($Context.Alias)-k001"
                money=@{currency='CNY';amountMinor='12000'}; paymentMethod='CARD'
            } @(400,409)
            $detail = Get-PaymentDetail $first.paymentId
            $listed = (Invoke-AcHttp POST '/api/payments/search' @{merchantId=$Context.MerchantId;pageSize=10} @(200)).Body
            Assert-AcEqual (Get-AcErrorCode $conflict.Body) 'IDEMPOTENCY_CONFLICT' 'conflicting payload returns IDEMPOTENCY_CONFLICT'
            Assert-AcEqual $detail.money.amountMinor '10000' 'original amount remains unchanged'
            Assert-AcEqual $listed.items.Count 1 'conflict creates no payment'
            Add-AcObservation 'originalPayment' $detail
        }
        'PAY-AC-004' {
            $flow = New-AcSucceededPayment $Context 100 'success'
            Assert-AcEqual $flow.Detail.status 'SUCCEEDED' 'trusted callback succeeds payment'
            Assert-Ac -Condition ([bool]$flow.Detail.successFactFormed) -Message 'one success fact is formed' -Actual $flow.Detail.successFactFormed
            Assert-AcEqual $flow.Detail.channelTransactionId $flow.TransactionId 'channel transaction identity is retained'
            $notifications = Search-Notifications @{merchantId=$Context.MerchantId;paymentId=$flow.PaymentId;sourceKind='PAYMENT';pageSize=10}
            Assert-AcEqual $notifications.items.Count 1 'one merchant success notification intent exists'
            Add-AcObservation 'payment' $flow.Detail; Add-AcObservation 'notifications' $notifications
        }
        'PAY-AC-005' {
            $created = New-AcPayment $Context 100 'CNY' 'dup' 'dup'
            $attempt = New-AcPaymentAttempt $Context $created.paymentId 'dup'
            Submit-AcPaymentAttempt $Context $created.paymentId $attempt.paymentAttemptId 'dup' | Out-Null
            $first = Send-AcPaymentResult $Context $created.paymentId $attempt.paymentAttemptId 100 'SUCCESS' 'dup' $true
            1..3 | ForEach-Object {
                $repeat = Invoke-AcHttp POST '/api/channel/payment-results' @{
                    channelId='C-001';notificationId=$first.NotificationId;paymentId=$created.paymentId;paymentAttemptId=$attempt.paymentAttemptId
                    channelTransactionId=$first.TransactionId;money=@{currency='CNY';amountMinor='10000'};result='SUCCESS';occurredAt=$first.OccurredAt;rawPayload=$first.RawPayload
                } @(200)
                Assert-AcEqual $repeat.Body.disposition 'DUPLICATE' "repeat $_ has the unified duplicate disposition"
            }
            $detail = Get-PaymentDetail $created.paymentId
            Assert-AcEqual $detail.attempts[0].notificationReceiveCount 4 'all four callback receipts are counted'
            Assert-Ac -Condition ([bool]$detail.successFactFormed) -Message 'duplicates retain one success fact' -Actual $detail.successFactFormed
            Assert-AcEqual $detail.merchantSuccessNotificationIntentCount 1 'duplicates do not create another merchant notification intent'
            Add-AcObservation 'paymentReceipts' $detail.attempts[0].notificationReceipts
        }
        'PAY-AC-006' {
            $created = New-AcPayment $Context 100 'CNY' 'invalid' 'invalid'
            $attempt = New-AcPaymentAttempt $Context $created.paymentId 'invalid'
            Submit-AcPaymentAttempt $Context $created.paymentId $attempt.paymentAttemptId 'invalid' | Out-Null
            $invalid = Send-AcPaymentResult $Context $created.paymentId $attempt.paymentAttemptId 99 'SUCCESS' 'invalid' $false
            $detail = Get-PaymentDetail $created.paymentId
            Assert-AcEqual $invalid.Body.disposition 'REJECTED_INVALID' 'untrusted or mismatched callback is rejected explicitly'
            Assert-AcEqual $detail.status 'PROCESSING' 'invalid callback does not advance payment'
            Assert-AcEqual $detail.rejectedNotificationCount 1 'invalid receipt is retained'
            Add-AcObservation 'payment' $detail
        }
        'PAY-AC-007' {
            $created = New-AcPayment $Context 100 'CNY' 'expiry' 'expiry' $Context.BaseInstant.AddMinutes(1)
            Set-AcClock $Context.BaseInstant.AddMinutes(2) | Out-Null
            $maintenance = (Invoke-AcHttp POST '/api/reference-fixtures/maintenance' @{action='PAYMENT_EXPIRY'} @(200)).Body
            $detail = Get-PaymentDetail $created.paymentId
            Assert-AcEqual $detail.status 'CLOSED' 'expired PAYABLE payment closes'
            Assert-Ac -Condition ($maintenance.changedCount -ge 1) -Message 'expiry maintenance reports a close' -Actual $maintenance
            Add-AcObservation 'payment' $detail
        }
        'PAY-AC-008' {
            $created = New-AcPayment $Context 100 'CNY' 'unknown' 'unknown' $Context.BaseInstant.AddMinutes(1)
            Invoke-AcHttp POST '/api/reference-fixtures/payment-channel-script' @{channelId='C-001';script='NO_RESULT'} @(200) | Out-Null
            $attempt = New-AcPaymentAttempt $Context $created.paymentId 'unknown'
            Submit-AcPaymentAttempt $Context $created.paymentId $attempt.paymentAttemptId 'unknown' | Out-Null
            Set-AcClock $Context.BaseInstant.AddMinutes(10) | Out-Null
            Invoke-AcHttp POST '/api/reference-fixtures/maintenance' @{action='PAYMENT_EXPIRY'} @(200) | Out-Null
            $detail = Get-PaymentDetail $created.paymentId
            $reviews = Search-ManualReviews @{merchantId=$Context.MerchantId;relatedResourceId=$created.paymentId;pageSize=10}
            Assert-AcEqual $detail.status 'RESULT_UNKNOWN' 'unknown attempt remains RESULT_UNKNOWN at expiry'
            Assert-AcEqual $detail.finality 'REVIEW_REQUIRED' 'overdue unknown attempt requires review'
            Assert-Ac -Condition ($reviews.items.Count -ge 1) -Message 'unknown expiry creates a review item' -Actual $reviews
            Add-AcObservation 'payment' $detail; Add-AcObservation 'manualReviews' $reviews
        }
        'PAY-AC-009' {
            $created = New-AcPayment $Context 100 'CNY' 'late' 'late' $Context.BaseInstant.AddMinutes(2)
            Invoke-AcHttp POST '/api/reference-fixtures/payment-channel-script' @{channelId='C-001';script='REJECT_ON_SUBMIT'} @(200) | Out-Null
            $attempt = New-AcPaymentAttempt $Context $created.paymentId 'late'
            Submit-AcPaymentAttempt $Context $created.paymentId $attempt.paymentAttemptId 'late' | Out-Null
            Set-AcClock $Context.BaseInstant.AddMinutes(3) | Out-Null
            Invoke-AcHttp POST '/api/reference-fixtures/maintenance' @{action='PAYMENT_EXPIRY'} @(200) | Out-Null
            $late = Send-AcPaymentResult $Context $created.paymentId $attempt.paymentAttemptId 100 'SUCCESS' 'late' $true
            $detail = Get-PaymentDetail $created.paymentId
            $reviews = Search-ManualReviews @{merchantId=$Context.MerchantId;relatedResourceId=$created.paymentId;pageSize=10}
            Assert-AcEqual $detail.status 'CLOSED' 'late success does not overwrite CLOSED fact'
            Assert-AcEqual $late.Body.disposition 'LATE' 'late success has the unified late disposition'
            Assert-AcEqual $detail.finality 'REVIEW_REQUIRED' 'late success leaves the closed payment review-required'
            Assert-Ac -Condition ($reviews.items.Count -ge 1) -Message 'late success creates review' -Actual $reviews
            Assert-Ac -Condition (-not [bool]$detail.settlementEligible) -Message 'late success blocks automatic settlement' -Actual $detail.settlementEligible
            Add-AcObservation 'payment' $detail; Add-AcObservation 'manualReviews' $reviews
        }
        'PAY-AC-010' {
            $created = New-AcPayment $Context 100 'CNY' 'double' 'double'
            $a1 = New-AcPaymentAttempt $Context $created.paymentId 'a1'
            $a2 = New-AcPaymentAttempt $Context $created.paymentId 'a2' 'explicit concurrent-attempt acceptance test'
            Submit-AcPaymentAttempt $Context $created.paymentId $a1.paymentAttemptId 'a1' | Out-Null
            Submit-AcPaymentAttempt $Context $created.paymentId $a2.paymentAttemptId 'a2' | Out-Null
            Send-AcPaymentResult $Context $created.paymentId $a1.paymentAttemptId 100 'SUCCESS' 'a1' $true | Out-Null
            $second = Send-AcPaymentResult $Context $created.paymentId $a2.paymentAttemptId 100 'SUCCESS' 'a2' $true
            $detail = Get-PaymentDetail $created.paymentId
            Assert-AcEqual $detail.status 'SUCCEEDED' 'payment remains succeeded'
            Assert-AcEqual $second.Body.disposition 'CONFLICTING' 'second successful attempt is conflicting'
            Assert-Ac -Condition ($detail.blockingReviewCount -ge 1) -Message 'double success opens blocking review' -Actual $detail.blockingReviewCount
            Assert-AcEqual $detail.merchantSuccessNotificationIntentCount 1 'only one success notification/fact is formed'
            Add-AcObservation 'payment' $detail
        }
        'PAY-AC-011' {
            $flow = New-AcSucceededPayment $Context 100 'paid-order'
            $second = Invoke-AcHttp POST '/api/payments' @{
                merchantId=$Context.MerchantId;merchantOrderNumber="$($Context.Alias)-order-paid-order";idempotencyKey="$($Context.Alias)-new-key"
                money=@{currency='CNY';amountMinor='10000'};paymentMethod='CARD'
            } @(400,409)
            $listed = (Invoke-AcHttp POST '/api/payments/search' @{merchantId=$Context.MerchantId;merchantOrderId="$($Context.Alias)-order-paid-order";pageSize=10} @(200)).Body
            Assert-AcEqual (Get-AcErrorCode $second.Body) 'ORDER_ALREADY_PAID' 'paid order rejects another collection with a stable code'
            Assert-AcEqual $listed.items.Count 1 'paid order still has one payment'
            Add-AcObservation 'payments' $listed
        }
        'PAY-AC-012' {
            $before = (Invoke-AcHttp POST '/api/payments/search' @{merchantId=$Context.MerchantId;pageSize=100} @(200)).Body
            $zero = Invoke-AcHttp POST '/api/payments' @{
                merchantId=$Context.MerchantId;merchantOrderNumber="$($Context.Alias)-zero";idempotencyKey="$($Context.Alias)-zero"
                money=@{currency='CNY';amountMinor='0'};paymentMethod='CARD'
            } @(400)
            $usd = Invoke-AcHttp POST '/api/payments' @{
                merchantId=$Context.MerchantId;merchantOrderNumber="$($Context.Alias)-usd";idempotencyKey="$($Context.Alias)-usd"
                money=@{currency='USD';amountMinor='1000'};paymentMethod='CARD'
            } @(400)
            $after = (Invoke-AcHttp POST '/api/payments/search' @{merchantId=$Context.MerchantId;pageSize=100} @(200)).Body
            Assert-AcEqual (Get-AcErrorCode $zero.Body) 'VALIDATION_ERROR' 'zero Money is rejected as VALIDATION_ERROR'
            Assert-AcEqual (Get-AcErrorCode $usd.Body) 'VALIDATION_ERROR' 'disabled currency is rejected as VALIDATION_ERROR'
            Assert-AcEqual $after.items.Count $before.items.Count 'invalid Money creates no payment'
            Add-AcObservation 'paymentsAfterRejections' $after
        }
        'PAY-AC-013' {
            $created = New-AcPayment $Context 100 'CNY' 'immutable' 'immutable'
            $update = Invoke-AcHttp PUT "/api/payments/$($created.paymentId)" @{money=@{currency='CNY';amountMinor='12000'}} @(405)
            $detail = Get-PaymentDetail $created.paymentId
            Assert-AcEqual (Get-AcErrorCode $update.Body) 'INVALID_STATE_TRANSITION' 'payment exposes no in-place Money mutation command'
            Assert-AcEqual $detail.money.amountMinor '10000' 'payment Money remains immutable'
            Add-AcObservation 'payment' $detail
        }
        'PAY-AC-014' {
            Invoke-AcHttp POST '/api/reference-fixtures/policy' @{feeRate=0.006;roundingMode='HALF_UP'} @(200) | Out-Null
            $flow = New-AcSucceededPayment $Context 100 'fee'
            Invoke-AcHttp POST '/api/reference-fixtures/policy' @{feeRate=0.008;roundingMode='HALF_UP'} @(200) | Out-Null
            $detail = Get-PaymentDetail $flow.PaymentId
            Assert-AcEqual ([decimal]$detail.feeSnapshot.feeRate) ([decimal]0.006) 'success snapshots active feeRate'
            Assert-AcEqual $detail.feeSnapshot.roundingMode 'HALF_UP' 'success snapshots rounding mode'
            Assert-AcEqual $detail.feeSnapshot.currencyPrecision 2 'success snapshots currency precision'
            Assert-AcEqual $detail.feeSnapshot.feeMoney.amountMinor '60' 'frozen fee amount is based on success policy'
            Add-AcObservation 'paymentFeeSnapshot' $detail.feeSnapshot
        }
        'PAY-AC-015' {
            $flow = New-AcSucceededPayment $Context 100 'conflict'
            $failure = Send-AcPaymentResult $Context $flow.PaymentId $flow.Attempt.paymentAttemptId 100 'FAILED' 'late-failure' $true $flow.TransactionId
            $detail = Get-PaymentDetail $flow.PaymentId
            Assert-AcEqual $detail.status 'SUCCEEDED' 'failure after success cannot roll back payment'
            Assert-AcEqual $failure.Body.disposition 'CONFLICTING' 'late failure is retained as conflict evidence'
            Assert-Ac -Condition ($detail.blockingReviewCount -ge 1) -Message 'conflict opens review' -Actual $detail.blockingReviewCount
            Add-AcObservation 'payment' $detail
        }
        'PAY-AC-016' {
            $created = New-AcPayment $Context 100 'CNY' 'accepted' 'accepted'
            $attempt = New-AcPaymentAttempt $Context $created.paymentId 'accepted'
            $submission = Submit-AcPaymentAttempt $Context $created.paymentId $attempt.paymentAttemptId 'accepted'
            $detail = Get-PaymentDetail $created.paymentId
            Assert-AcEqual $submission.attemptStatus 'ACCEPTED' 'default reference channel accepts the submission'
            Assert-AcEqual $detail.status 'PROCESSING' 'channel acceptance leaves payment processing'
            Assert-Ac -Condition (-not [bool]$detail.successFactFormed) -Message 'acceptance forms no income fact' -Actual $detail.successFactFormed
            Assert-AcEqual $detail.merchantSuccessNotificationIntentCount 0 'acceptance forms no success notification'
            Add-AcObservation 'payment' $detail
        }
        'PAY-AC-017' {
            $flow = New-AcSucceededPayment $Context 100 'terminal'
            $before = $flow.Detail.attemptCount
            $rejected = Invoke-AcHttp POST "/api/payments/$($flow.PaymentId)/attempts" @{idempotencyKey="$($Context.Alias)-after-success"} @(400,409)
            $detail = Get-PaymentDetail $flow.PaymentId
            Assert-AcEqual (Get-AcErrorCode $rejected.Body) 'PAYMENT_STATE_CONFLICT' 'new attempt after success is synchronously rejected'
            Assert-AcEqual $detail.attemptCount $before 'rejection leaves attempt history unchanged'
            Add-AcObservation 'payment' $detail
        }
        default { throw "Unknown payment scenario $Id" }
    }
}

function Invoke-RefundPayAcScenario {
    param([string]$Id, $Context)
    switch ($Id) {
        'PAY-AC-020' {
            $payment = New-AcSucceededPayment $Context 100 'full'
            $refund = New-AcSucceededRefund $Context $payment.PaymentId 100 'full'
            Assert-AcEqual $refund.Detail.status 'SUCCEEDED' 'full refund succeeds'
            Assert-AcEqual $refund.Payment.refundBudget.succeededAmount.amountMinor '10000' 'successful refund total is 100'
            Assert-AcEqual $refund.Payment.refundBudget.availableAmount.amountMinor '0' 'refundable amount is zero'
            Add-AcObservation 'refund' $refund.Detail; Add-AcObservation 'refundBudget' $refund.Payment.refundBudget
        }
        'PAY-AC-021' {
            $payment = New-AcSucceededPayment $Context 100 'parts'
            $r1 = New-AcSucceededRefund $Context $payment.PaymentId 30 'part1'
            $r2 = New-AcSucceededRefund $Context $payment.PaymentId 20 'part2'
            $detail = Get-PaymentDetail $payment.PaymentId
            Assert-AcEqual $detail.refundBudget.succeededAmount.amountMinor '5000' 'partial refund total is 50'
            Assert-AcEqual $detail.refundBudget.availableAmount.amountMinor '5000' 'remaining refundable amount is 50'
            Assert-Ac -Condition ($r1.RefundId -ne $r2.RefundId) -Message 'partial refunds have independent identities' -Actual @($r1.RefundId,$r2.RefundId)
            Add-AcObservation 'refundBudget' $detail
        }
        'PAY-AC-022' {
            $payment = New-AcSucceededPayment $Context 100 'over'
            New-AcSucceededRefund $Context $payment.PaymentId 60 'existing' | Out-Null
            $rejected = Invoke-AcHttp POST '/api/refunds' @{
                merchantId=$Context.MerchantId;idempotencyKey="$($Context.Alias)-over";merchantRefundNo="$($Context.Alias)-over"
                paymentId=$payment.PaymentId;money=@{currency='CNY';amountMinor='5000'};reason='over budget'
            } @(400,409)
            $detail = Get-PaymentDetail $payment.PaymentId
            $listed = (Invoke-AcHttp POST '/api/refunds/search' @{merchantId=$Context.MerchantId;paymentId=$payment.PaymentId;pageSize=10} @(200)).Body
            Assert-AcEqual (Get-AcErrorCode $rejected.Body) 'REFUND_BUDGET_EXCEEDED' 'over-refund is rejected with stable budget error'
            Assert-AcEqual $detail.refundBudget.availableAmount.amountMinor '4000' 'budget remains 40 after rejection'
            Assert-AcEqual $listed.items.Count 1 'rejected over-refund creates no Refund'
            Add-AcObservation 'refundBudget' $detail
        }
        'PAY-AC-023' {
            $payment = New-AcSucceededPayment $Context 100 'concurrent'
            New-AcSucceededRefund $Context $payment.PaymentId 40 'prior' | Out-Null
            $baseUrl = Get-AcBaseUrl
            $bodies = 1..2 | ForEach-Object {
                @{ merchantId=$Context.MerchantId;idempotencyKey="$($Context.Alias)-parallel-$_";merchantRefundNo="$($Context.Alias)-parallel-$_";paymentId=$payment.PaymentId;money=@{currency='CNY';amountMinor='4000'};reason="parallel $_" }
            }
            $jobs = foreach ($body in $bodies) {
                $json = $body | ConvertTo-Json -Depth 10 -Compress
                Start-Job -ScriptBlock {
                    param($Uri,$Json)
                    $r = Invoke-WebRequest -Uri $Uri -Method POST -ContentType 'application/json' -Body $Json -SkipHttpErrorCheck
                    [pscustomobject]@{Pid=$PID;Status=[int]$r.StatusCode;Text=$r.Content}
                } -ArgumentList "$baseUrl/api/refunds",$json
            }
            $results = $jobs | Wait-Job | Receive-Job
            $jobs | Remove-Job -Force
            for ($i=0; $i -lt $results.Count; $i++) {
                $parsed = try { $results[$i].Text | ConvertFrom-Json -Depth 20 } catch { $results[$i].Text }
                Add-AcExternalHttpTrace 'POST' '/api/refunds' $bodies[$i] $results[$i].Status $parsed $results[$i].Pid
            }
            $accepted = @($results | Where-Object Status -eq 201)
            $rejected = @($results | Where-Object Status -eq 409)
            $detail = Get-PaymentDetail $payment.PaymentId
            Assert-AcEqual $accepted.Count 1 'exactly one concurrent 40 refund reserves remaining 60 budget'
            Assert-AcEqual $rejected.Count 1 'the other concurrent refund is rejected'
            $rejectedBody = $rejected[0].Text | ConvertFrom-Json -Depth 20
            Assert-AcEqual (Get-AcErrorCode $rejectedBody) 'REFUND_BUDGET_EXCEEDED' 'concurrent loser is rejected by the refund budget invariant'
            Assert-Ac -Condition (([int64]$detail.refundBudget.reservedAmount.amountMinor + [int64]$detail.refundBudget.succeededAmount.amountMinor) -le 10000) -Message 'concurrent budget never exceeds original amount' -Actual $detail
            Add-AcObservation 'refundBudget' $detail
        }
        'PAY-AC-024' {
            $payment = New-AcSucceededPayment $Context 100 'failure'
            Set-AcClock $Context.BaseInstant.AddMinutes(3) | Out-Null
            $refund = New-AcRefundRequest $Context $payment.PaymentId 40 'failure'
            $attempt = New-AcRefundAttempt $Context $refund.refundId 'failure'
            Submit-AcRefundAttempt $Context $refund.refundId $attempt.refundAttemptId 'failure' | Out-Null
            $before = Get-RefundDetail $refund.refundId
            $result = Send-AcRefundResult $Context $refund.refundId $attempt.refundAttemptId $before.attempts[0].channelRefundId 40 'FAILED' 'failure' $true
            $detail = Get-RefundDetail $refund.refundId; $budget = Get-PaymentDetail $payment.PaymentId
            Assert-AcEqual $detail.status 'FAILED' 'explicit refund failure is terminal failed'
            Assert-Ac -Condition ([bool]$result.Body.reservationReleasedNow) -Message 'failure releases reservation once' -Actual $result.Body
            Assert-AcEqual $budget.refundBudget.availableAmount.amountMinor '10000' 'refund budget is fully restored'
            Add-AcObservation 'refund' $detail; Add-AcObservation 'refundBudget' $budget
        }
        'PAY-AC-025' {
            $payment = New-AcSucceededPayment $Context 100 'unknown'
            Set-AcClock $Context.BaseInstant.AddMinutes(3) | Out-Null
            $refund = New-AcRefundRequest $Context $payment.PaymentId 40 'unknown'
            $attempt = New-AcRefundAttempt $Context $refund.refundId 'unknown'
            Submit-AcRefundAttempt $Context $refund.refundId $attempt.refundAttemptId 'unknown' | Out-Null
            $before = Get-RefundDetail $refund.refundId
            Send-AcRefundResult $Context $refund.refundId $attempt.refundAttemptId $before.attempts[0].channelRefundId 40 'UNKNOWN' 'unknown' $true | Out-Null
            Set-AcClock $Context.BaseInstant.AddMinutes(20) | Out-Null
            Invoke-AcHttp POST '/api/reference-fixtures/maintenance' @{action='REFUND_UNKNOWN_REVIEW'} @(200) | Out-Null
            $detail = Get-RefundDetail $refund.refundId; $budget = Get-PaymentDetail $payment.PaymentId
            $reviews = Search-ManualReviews @{merchantId=$Context.MerchantId;relatedResourceId=$refund.refundId;pageSize=10}
            Assert-AcEqual $detail.status 'RESULT_UNKNOWN' 'unknown refund retains the public unknown status'
            Assert-AcEqual $detail.finality 'REVIEW_REQUIRED' 'overdue unknown refund requires review'
            Assert-AcEqual $budget.refundBudget.reservedAmount.amountMinor '4000' 'unknown result keeps refund reservation'
            Assert-Ac -Condition ($reviews.items.Count -ge 1) -Message 'unknown refund creates manual review' -Actual $reviews
            Add-AcObservation 'refund' $detail; Add-AcObservation 'refundBudget' $budget; Add-AcObservation 'manualReviews' $reviews
        }
        'PAY-AC-026' {
            $payment = New-AcSucceededPayment $Context 100 'replay'
            Set-AcClock $Context.BaseInstant.AddMinutes(3) | Out-Null
            $first = New-AcRefundRequest $Context $payment.PaymentId 20 'replay'
            $second = New-AcRefundRequest $Context $payment.PaymentId 20 'replay'
            $budget = Get-PaymentDetail $payment.PaymentId
            Assert-AcEqual $second.refundId $first.refundId 'refund replay returns original Refund'
            Assert-AcEqual $second.receipt.operationId $first.receipt.operationId 'refund replay returns original Operation'
            Assert-AcEqual $second.receipt.acceptanceStatus 'ALREADY_ACCEPTED' 'refund replay is ALREADY_ACCEPTED'
            Assert-AcEqual $budget.refundBudget.reservedAmount.amountMinor '2000' 'refund replay reserves only once'
            Add-AcObservation 'refundBudget' $budget
        }
        'PAY-AC-027' {
            Invoke-AcHttp POST '/api/reference-fixtures/policy' @{paymentExpiry='P30D'} @(200) | Out-Null

            $processingContext = New-AcChildContext $Context 'processing' 0
            $processing = New-AcPayment $processingContext 100 'CNY' 'not-paid' 'not-paid'
            $processingAttempt = New-AcPaymentAttempt $processingContext $processing.paymentId 'not-paid'
            Submit-AcPaymentAttempt $processingContext $processing.paymentId $processingAttempt.paymentAttemptId 'not-paid' | Out-Null

            $unknownContext = New-AcChildContext $Context 'unknown' 1
            Set-AcClock $unknownContext.BaseInstant | Out-Null
            $unknown = New-AcPayment $unknownContext 100 'CNY' 'not-paid' 'not-paid'
            $unknownAttempt = New-AcPaymentAttempt $unknownContext $unknown.paymentId 'not-paid'
            Submit-AcPaymentAttempt $unknownContext $unknown.paymentId $unknownAttempt.paymentAttemptId 'not-paid' | Out-Null
            Send-AcPaymentResult $unknownContext $unknown.paymentId $unknownAttempt.paymentAttemptId 100 'UNKNOWN' 'not-paid' $true | Out-Null

            $failedContext = New-AcChildContext $Context 'failed' 2
            Set-AcClock $failedContext.BaseInstant | Out-Null
            Invoke-AcHttp POST '/api/reference-fixtures/policy' @{paymentExpiry='PT1M'} @(200) | Out-Null
            $failed = New-AcPayment $failedContext 100 'CNY' 'not-paid' 'not-paid'
            $failedAttempt = New-AcPaymentAttempt $failedContext $failed.paymentId 'not-paid'
            Submit-AcPaymentAttempt $failedContext $failed.paymentId $failedAttempt.paymentAttemptId 'not-paid' | Out-Null
            Set-AcClock $failedContext.BaseInstant.AddMinutes(2) | Out-Null
            Send-AcPaymentResult $failedContext $failed.paymentId $failedAttempt.paymentAttemptId 100 'FAILED' 'not-paid' $true | Out-Null

            $closedContext = New-AcChildContext $Context 'closed' 3
            Set-AcClock $closedContext.BaseInstant | Out-Null
            Invoke-AcHttp POST '/api/reference-fixtures/policy' @{paymentExpiry='PT1M'} @(200) | Out-Null
            $closed = New-AcPayment $closedContext 100 'CNY' 'not-paid' 'not-paid'
            Set-AcClock $closedContext.BaseInstant.AddMinutes(2) | Out-Null
            Invoke-AcHttp POST '/api/reference-fixtures/maintenance' @{action='PAYMENT_EXPIRY'} @(200) | Out-Null

            $cases = @(
                @{name='PROCESSING';context=$processingContext;paymentId=$processing.paymentId},
                @{name='RESULT_UNKNOWN';context=$unknownContext;paymentId=$unknown.paymentId},
                @{name='FAILED';context=$failedContext;paymentId=$failed.paymentId},
                @{name='CLOSED';context=$closedContext;paymentId=$closed.paymentId}
            )
            $observed = @()
            foreach ($case in $cases) {
                $before = Get-PaymentDetail $case.paymentId
                Assert-AcEqual $before.status $case.name "$($case.name) fixture reaches the required non-success state"
                $attemptCountBefore = @($before.attempts).Count
                $submissionReceiptCountBefore = @($before.attempts | ForEach-Object { @($_.submissionReceipts).Count } | Measure-Object -Sum).Sum
                if ($null -eq $submissionReceiptCountBefore) { $submissionReceiptCountBefore = 0 }
                $rejected = Invoke-AcHttp POST '/api/refunds' @{
                    merchantId=$case.context.MerchantId;idempotencyKey="$($case.context.Alias)-not-paid-refund";merchantRefundNo="$($case.context.Alias)-not-paid-refund"
                    paymentId=$case.paymentId;money=@{currency='CNY';amountMinor='1000'};reason="refund forbidden for $($case.name) payment"
                } @(400,409)
                $after = Get-PaymentDetail $case.paymentId
                $listed = (Invoke-AcHttp POST '/api/refunds/search' @{merchantId=$case.context.MerchantId;paymentId=$case.paymentId;pageSize=10} @(200)).Body
                $submissionReceiptCountAfter = @($after.attempts | ForEach-Object { @($_.submissionReceipts).Count } | Measure-Object -Sum).Sum
                if ($null -eq $submissionReceiptCountAfter) { $submissionReceiptCountAfter = 0 }
                Assert-AcEqual (Get-AcErrorCode $rejected.Body) 'VALIDATION_ERROR' "$($case.name) payment cannot be refunded"
                Assert-AcEqual $listed.items.Count 0 "$($case.name) rejection creates no Refund or refund attempt"
                foreach($field in @('originalAmount','succeededAmount','reservedAmount','availableAmount')) {
                    Assert-AcEqual $after.refundBudget.$field.currency $before.refundBudget.$field.currency "$($case.name) rejection keeps $field currency"
                    Assert-AcEqual $after.refundBudget.$field.amountMinor $before.refundBudget.$field.amountMinor "$($case.name) rejection keeps $field amount"
                }
                Assert-AcEqual @($after.attempts).Count $attemptCountBefore "$($case.name) rejection creates no payment attempt side effect"
                Assert-AcEqual $submissionReceiptCountAfter $submissionReceiptCountBefore "$($case.name) rejection creates no channel submission side effect"
                $observed += [pscustomobject]@{status=$after.status;refundCount=$listed.items.Count;refundBudget=$after.refundBudget;attemptCount=$attemptCountBefore;submissionReceiptCount=$submissionReceiptCountBefore}
            }
            Add-AcObservation 'nonSuccessfulRefundRejections' $observed
        }
        'PAY-AC-028' {
            $payment = New-AcSucceededPayment $Context 100 'window'
            Set-AcClock $Context.BaseInstant.AddDays(31) | Out-Null
            $rejected = Invoke-AcHttp POST '/api/refunds' @{
                merchantId=$Context.MerchantId;idempotencyKey="$($Context.Alias)-expired";merchantRefundNo="$($Context.Alias)-expired"
                paymentId=$payment.PaymentId;money=@{currency='CNY';amountMinor='1000'};reason='outside refund window'
            } @(400,409)
            $detail = Get-PaymentDetail $payment.PaymentId
            Assert-AcEqual (Get-AcErrorCode $rejected.Body) 'VALIDATION_ERROR' 'refund after policy window is rejected'
            Assert-AcEqual $detail.refundBudget.availableAmount.amountMinor '10000' 'expired request does not change budget'
            Add-AcObservation 'refundBudget' $detail
        }
        'PAY-AC-029' {
            $payment = New-AcSucceededPayment $Context 100 'refund-conflict'
            $refund = New-AcSucceededRefund $Context $payment.PaymentId 40 'refund-conflict'
            $late = Send-AcRefundResult $Context $refund.RefundId $refund.Attempt.refundAttemptId $refund.ChannelRefundId 40 'FAILED' 'late-failure' $true
            $detail = Get-RefundDetail $refund.RefundId; $budget = Get-PaymentDetail $payment.PaymentId
            Assert-AcEqual $detail.status 'SUCCEEDED' 'failure after refund success cannot roll back refund'
            Assert-AcEqual $late.Body.disposition 'CONFLICTING' 'late failure is retained as refund conflict'
            Assert-AcEqual $budget.refundBudget.succeededAmount.amountMinor '4000' 'successful refund budget is not rolled back'
            Add-AcObservation 'refund' $detail; Add-AcObservation 'refundBudget' $budget
        }
        default { throw "Unknown refund scenario $Id" }
    }
}

function Invoke-ReconciliationPayAcScenario {
    param([string]$Id, $Context)
    switch ($Id) {
        'PAY-AC-040' {
            $payment = New-AcSucceededPayment $Context 100 'matched'
            $record = New-AcBillRecord "$($Context.Alias)-matched" 'PAYMENT' $payment.TransactionId 100 $payment.OccurredAt
            $bill = Register-AcBillAndSignal $Context @($record) '1' 'matched'
            Assert-AcEqual $bill.Run.status 'COMPLETED' 'fully matched run completes'
            Assert-AcEqual $bill.Run.matchedCount 1 'one platform fact matches one bill record'
            Assert-AcEqual $bill.Run.differenceCount 0 'matched run has no differences'
            Add-AcObservation 'reconciliationRun' $bill.Run
        }
        'PAY-AC-041' {
            $payment = New-AcSucceededPayment $Context 100 'platform-only'
            $bill = Register-AcBillAndSignal $Context @() '1' 'platform-only'
            $item = @($bill.Run.differences | Where-Object differenceType -eq 'PLATFORM_ONLY')[0]
            Assert-AcEqual $bill.Run.status 'ACTION_REQUIRED' 'platform-only run requires action'
            Assert-Ac -Condition ($null -ne $item) -Message 'platform-only difference is materialized' -Actual $bill.Run.differences
            Assert-Ac -Condition ([bool]$item.settlementBlocked) -Message 'platform-only difference blocks settlement' -Actual $item
            Assert-AcEqual $item.paymentId $payment.PaymentId 'difference retains platform payment reference'
            Add-AcObservation 'reconciliationRun' $bill.Run
        }
        'PAY-AC-042' {
            $record = New-AcBillRecord "$($Context.Alias)-channel-only" 'PAYMENT' "UNKNOWN-$($Context.Alias)" 100 $Context.BaseInstant.AddMinutes(2).ToString('o')
            $bill = Register-AcBillAndSignal $Context @($record) '1' 'channel-only'
            $item = @($bill.Run.differences | Where-Object differenceType -eq 'CHANNEL_ONLY')[0]
            $reviews = Search-ManualReviews @{type='RECONCILIATION_DIFFERENCE';relatedResourceId=$bill.Signal.runId;pageSize=20}
            Assert-Ac -Condition ($null -ne $item) -Message 'channel-only difference is materialized' -Actual $bill.Run.differences
            Assert-Ac -Condition ($null -eq $item.paymentId) -Message 'channel-only evidence does not invent a payment' -Actual $item.paymentId
            Assert-Ac -Condition ($bill.Run.settlementBlocked) -Message 'channel-only evidence remains blocked for investigation' -Actual $bill.Run
            Add-AcObservation 'reconciliationRun' $bill.Run; Add-AcObservation 'manualReviews' $reviews
        }
        'PAY-AC-043' {
            $payment = New-AcSucceededPayment $Context 100 'amount-mismatch'
            $record = New-AcBillRecord "$($Context.Alias)-amount" 'PAYMENT' $payment.TransactionId 99 $payment.OccurredAt
            $bill = Register-AcBillAndSignal $Context @($record) '1' 'amount-mismatch'
            $item = @($bill.Run.differences | Where-Object differenceType -eq 'AMOUNT_MISMATCH')[0]
            Assert-Ac -Condition ($null -ne $item) -Message 'amount mismatch is classified explicitly' -Actual $bill.Run.differences
            Assert-AcEqual $item.platformMoney.amountMinor '10000' 'platform original Money is retained'
            Assert-AcEqual $item.channelMoney.amountMinor '9900' 'channel original Money is retained'
            Assert-Ac -Condition ([bool]$item.settlementBlocked) -Message 'amount mismatch blocks settlement' -Actual $item
            Add-AcObservation 'difference' $item
        }
        'PAY-AC-044' {
            $created = New-AcPayment $Context 100 'CNY' 'status' 'status'
            $attempt = New-AcPaymentAttempt $Context $created.paymentId 'status'
            Submit-AcPaymentAttempt $Context $created.paymentId $attempt.paymentAttemptId 'status' | Out-Null
            $unknown = Send-AcPaymentResult $Context $created.paymentId $attempt.paymentAttemptId 100 'UNKNOWN' 'status' $true
            $record = New-AcBillRecord "$($Context.Alias)-status" 'PAYMENT' $unknown.TransactionId 100 $unknown.OccurredAt 'SUCCEEDED'
            $bill = Register-AcBillAndSignal $Context @($record) '1' 'status'
            $item = @($bill.Run.differences | Where-Object differenceType -eq 'STATUS_MISMATCH')[0]
            Assert-Ac -Condition ($null -ne $item) -Message 'status mismatch is materialized' -Actual $bill.Run.differences
            $path = "/api/reconciliation-runs/$($bill.Signal.runId)/differences/$($item.itemId)/confirmations"
            $body = @{merchantId=$Context.MerchantId;channelId='C-001';reason='verified channel success';evidence='evidence://status-confirm';idempotencyKey="$($Context.Alias)-confirm"}
            $missing = Invoke-AcHttp POST $path $body @(400)
            $unknownActor = Invoke-AcHttp POST $path $body @(400) 'unknown-reference-actor'
            $before = (Invoke-AcHttp GET "/api/reconciliation-runs/$($bill.Signal.runId)" $null @(200)).Body
            $beforeItem = @($before.differences | Where-Object itemId -eq $item.itemId)[0]
            $confirmed = Invoke-AcHttp POST $path $body @(200) 'fixture-reconciliation-operator'
            $after = (Invoke-AcHttp GET "/api/reconciliation-runs/$($bill.Signal.runId)" $null @(200)).Body
            Assert-AcEqual (Get-AcErrorCode $missing.Body) 'VALIDATION_ERROR' 'missing actor context rejects confirmation'
            Assert-AcEqual (Get-AcErrorCode $unknownActor.Body) 'VALIDATION_ERROR' 'unknown actor context rejects confirmation'
            Assert-Ac -Condition (($missing.Body.PSObject.Properties.Name -notcontains 'receipt') -and ($unknownActor.Body.PSObject.Properties.Name -notcontains 'receipt')) -Message 'invalid actor contexts create no Operation receipt' -Actual @($missing.Body,$unknownActor.Body)
            Assert-AcEqual $beforeItem.confirmationFacts.Count 0 'invalid actor contexts append no confirmation fact'
            Assert-AcEqual $confirmed.Body.actorId 'reference-reconciliation-operator' 'confirmation exposes trusted actorId'
            Assert-AcEqual $confirmed.Body.receipt.acceptanceStatus 'ACCEPTED' 'valid actor is accepted after invalid attempts with the same key'
            Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace([string]$confirmed.Body.confirmationFactId)) -Message 'confirmation creates an independent fact' -Actual $confirmed.Body
            Assert-AcEqual $item.platformRawStatus 'RESULT_UNKNOWN' 'original platform unknown evidence is retained'
            Assert-AcEqual $item.channelRawStatus 'SUCCEEDED' 'original channel success evidence is retained'
            Add-AcObservation 'reconciliationRunAfterConfirmation' $after
        }
        'PAY-AC-045' {
            $p1 = New-AcSucceededPayment $Context 100 'rerun-p1'
            $p2 = New-AcSucceededPayment $Context 50 'rerun-p2'
            $revision1Records = @(
                (New-AcBillRecord "$($Context.Alias)-amount-mismatch" 'PAYMENT' $p1.TransactionId 99 $p1.OccurredAt),
                (New-AcBillRecord "$($Context.Alias)-channel-only" 'PAYMENT' "CT-$($Context.Alias)-channel-only" 25 $Context.BaseInstant.AddMinutes(4).ToString('o'))
            )
            $bill = Register-AcBillAndSignal $Context $revision1Records '1' 'rerun'
            Assert-AcEqual $bill.Run.differenceCount 3 'initial revision forms exactly three differences'
            $initialTypes = @($bill.Run.differences.differenceType | Sort-Object)
            Assert-Ac -Condition (($initialTypes -join ',') -eq 'AMOUNT_MISMATCH,CHANNEL_ONLY,PLATFORM_ONLY') -Message 'initial differences retain the three independent classifications' -Actual $initialTypes

            $revision2Records = @(
                (New-AcBillRecord "$($Context.Alias)-matched-p1" 'PAYMENT' $p1.TransactionId 100 $p1.OccurredAt),
                (New-AcBillRecord "$($Context.Alias)-matched-p2" 'PAYMENT' $p2.TransactionId 50 $p2.OccurredAt)
            )
            $registered2 = (Invoke-AcHttp POST '/api/reference-fixtures/bills' @{
                channelId='C-001';billIdentity=$bill.BillIdentity;businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai'
                revision='2';completeness='COMPLETE';rawEvidence="evidence://$($bill.BillIdentity)/2";payloadFingerprint="$($bill.BillIdentity)-2-fingerprint"
                publishedAt=$Context.BaseInstant.AddHours(13).ToString('o');records=$revision2Records
            } @(200)).Body
            $rerun = Invoke-AcHttp POST "/api/reconciliation-runs/$($bill.Signal.runId)/reruns" @{idempotencyKey="$($Context.Alias)-rerun-key"} @(200) 'fixture-reconciliation-operator'
            $replay = Invoke-AcHttp POST "/api/reconciliation-runs/$($bill.Signal.runId)/reruns" @{idempotencyKey="$($Context.Alias)-rerun-key"} @(200) 'fixture-reconciliation-operator'
            $rerunDetail = (Invoke-AcHttp GET "/api/reconciliation-runs/$($rerun.Body.runId)" $null @(200)).Body
            $signal2 = (Invoke-AcHttp POST "/api/reference-fixtures/bills/$($bill.BillIdentity)/signals" @{
                channelId='C-001';businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai'
                signalIdentity="SIGNAL-$($bill.BillIdentity)-2";announcedRevision='2';publishedAt=$Context.BaseInstant.AddHours(13).ToString('o')
            } @(200)).Body
            $revision2 = (Invoke-AcHttp GET "/api/reconciliation-runs/$($signal2.runId)" $null @(200)).Body
            $list = (Invoke-AcHttp POST '/api/reconciliation-runs/search' @{merchantId=$Context.MerchantId;channelId='C-001';businessDate=$Context.BusinessDate;pageSize=20} @(200)).Body
            Assert-AcEqual $replay.Body.receipt.operationId $rerun.Body.receipt.operationId 'same rerun command replays one operation'
            Assert-AcEqual $replay.Body.receipt.acceptanceStatus 'ALREADY_ACCEPTED' 'rerun replay is ALREADY_ACCEPTED'
            Assert-AcEqual $rerun.Body.billRevision '2' 'manual rerun pulls the newly registered authoritative revision'
            Assert-AcEqual $rerunDetail.differenceCount 0 'manual rerun records the result change to no differences'
            Assert-AcEqual $signal2.runId $rerun.Body.runId 'later signal for the already-run revision reuses the same effective run'
            Assert-Ac -Condition ([bool]$signal2.idempotentReplay) -Message 'later signal does not duplicate the revision-two run' -Actual $signal2
            Assert-AcEqual $registered2.currentRevision '2' 'higher corrected bill revision becomes authoritative'
            Assert-AcEqual $revision2.differenceCount 0 'corrected revision records the result change to no differences'
            $revision1Runs = @($list.items | Where-Object billRevision -eq '1')
            $revision2Runs = @($list.items | Where-Object billRevision -eq '2')
            Assert-Ac -Condition ($list.items.Count -ge 2) -Message 'initial and rerun executions remain distinguishable' -Actual $list
            Assert-AcEqual @($list.items.runId | Select-Object -Unique).Count $list.items.Count 'run history exposes distinct stable run identities'
            Assert-AcEqual $revision1Runs.Count 1 'initial three-difference run remains queryable exactly once'
            Assert-AcEqual $revision2Runs.Count 1 'corrected rerun remains queryable exactly once'
            Assert-Ac -Condition (@($list.items | Where-Object effectiveRun).Count -eq 1) -Message 'scope has exactly one effective run' -Actual $list
            Assert-Ac -Condition (@($list.items | Where-Object effectiveRun)[0].runId -eq $signal2.runId) -Message 'the corrected revision is the sole effective run' -Actual $list
            Add-AcObservation 'reconciliationRuns' $list
        }
        'PAY-AC-046' {
            $payment = New-AcSucceededPayment $Context 100 'blocked'
            $record = New-AcBillRecord "$($Context.Alias)-blocked" 'PAYMENT' $payment.TransactionId 99 $payment.OccurredAt
            $bill = Register-AcBillAndSignal $Context @($record) '1' 'blocked'
            Assert-AcEqual $bill.Run.status 'ACTION_REQUIRED' 'unresolved amount difference prevents completion'
            Assert-Ac -Condition ($bill.Run.unresolvedDifferenceCount -ge 1) -Message 'run exposes unresolved difference count' -Actual $bill.Run
            Assert-Ac -Condition ([bool]$bill.Run.settlementBlocked) -Message 'run exposes settlement blocker' -Actual $bill.Run
            Add-AcObservation 'reconciliationRun' $bill.Run
        }
        'PAY-AC-047' {
            $payment = New-AcSucceededPayment $Context 100 'disposition'
            $record = New-AcBillRecord "$($Context.Alias)-disposition" 'PAYMENT' $payment.TransactionId 99 $payment.OccurredAt
            $bill = Register-AcBillAndSignal $Context @($record) '1' 'disposition'
            $item = @($bill.Run.differences | Where-Object differenceType -eq 'AMOUNT_MISMATCH')[0]
            $path = "/api/reconciliation-runs/$($bill.Signal.runId)/differences/$($item.itemId)/dispositions"
            $body = @{
                merchantId=$Context.MerchantId;channelId='C-001';conclusion='NO_SETTLEMENT_IMPACT';settlementImpact='DOES_NOT_BLOCK_SETTLEMENT'
                reason='channel fee display explains difference';evidence='evidence://amount-investigation';followUp='retain both originals';idempotencyKey="$($Context.Alias)-dispose"
            }
            $missing = Invoke-AcHttp POST $path $body @(400)
            $unknownActor = Invoke-AcHttp POST $path $body @(400) 'unknown-reference-actor'
            $before = (Invoke-AcHttp GET "/api/reconciliation-runs/$($bill.Signal.runId)" $null @(200)).Body
            $beforeItem = @($before.differences | Where-Object itemId -eq $item.itemId)[0]
            $disposed = Invoke-AcHttp POST $path $body @(200) 'fixture-reconciliation-operator'
            $after = (Invoke-AcHttp GET "/api/reconciliation-runs/$($bill.Signal.runId)" $null @(200)).Body
            $afterItem = @($after.differences | Where-Object itemId -eq $item.itemId)[0]
            Assert-AcEqual (Get-AcErrorCode $missing.Body) 'VALIDATION_ERROR' 'missing actor context rejects disposition'
            Assert-AcEqual (Get-AcErrorCode $unknownActor.Body) 'VALIDATION_ERROR' 'unknown actor context rejects disposition'
            Assert-Ac -Condition (($missing.Body.PSObject.Properties.Name -notcontains 'receipt') -and ($unknownActor.Body.PSObject.Properties.Name -notcontains 'receipt')) -Message 'invalid actor contexts create no Operation receipt' -Actual @($missing.Body,$unknownActor.Body)
            Assert-AcEqual $beforeItem.dispositions.Count 0 'invalid actor contexts append no disposition'
            Assert-AcEqual $disposed.Body.actorId 'reference-reconciliation-operator' 'disposition exposes trusted actorId'
            Assert-AcEqual $disposed.Body.receipt.acceptanceStatus 'ACCEPTED' 'valid actor is accepted after invalid attempts with the same key'
            Assert-AcEqual $afterItem.platformMoney.amountMinor '10000' 'platform evidence remains unchanged'
            Assert-AcEqual $afterItem.channelMoney.amountMinor '9900' 'bill evidence remains unchanged'
            Assert-AcEqual $afterItem.differenceType 'AMOUNT_MISMATCH' 'initial difference classification remains visible'
            Assert-AcEqual $afterItem.dispositions.Count 1 'disposition is appended independently'
            Add-AcObservation 'differenceHistory' $afterItem
        }
        default { throw "Unknown reconciliation scenario $Id" }
    }
}

function Invoke-SettlementPayAcScenario {
    param([string]$Id, $Context)
    switch ($Id) {
        'PAY-AC-060' {
            Invoke-AcHttp POST '/api/reference-fixtures/policy' @{feeRate=0.02;roundingMode='HALF_UP'} @(200) | Out-Null
            $p1 = New-AcSucceededPayment $Context 100 'gross100'
            $p2 = New-AcSucceededPayment $Context 50 'gross50'
            $refund = New-AcSucceededRefund $Context $p1.PaymentId 20 'refund20'
            $records = @(
                (New-AcBillRecord "$($Context.Alias)-p1" 'PAYMENT' $p1.TransactionId 100 $p1.OccurredAt),
                (New-AcBillRecord "$($Context.Alias)-p2" 'PAYMENT' $p2.TransactionId 50 $p2.OccurredAt),
                (New-AcBillRecord "$($Context.Alias)-r1" 'REFUND' $refund.ChannelRefundId 20 $refund.OccurredAt)
            )
            $bill = Register-AcBillAndSignal $Context $records '1' 'normal'
            Assert-AcEqual $bill.Run.status 'COMPLETED' 'all settlement source facts reconcile'
            $prepared = New-AcPreparedSettlement $Context 'normal'
            $detail = Get-SettlementDetail $prepared.settlementId
            Assert-AcEqual $detail.grossMoney.amountMinor '15000' 'settlement gross is 150'
            Assert-AcEqual $detail.refundMoney.amountMinor '2000' 'settlement refund deduction is 20'
            Assert-AcEqual $detail.feeMoney.amountMinor '300' 'settlement fee is 3'
            Assert-AcEqual $detail.netMoney.amountMinor '12700' 'settlement net is 127'
            Assert-Ac -Condition ($detail.lines.Count -ge 3) -Message 'each amount component has traceable lines' -Actual $detail.lines
            Add-AcObservation 'settlement' $detail
        }
        'PAY-AC-061' {
            $eligible = New-AcSucceededPayment $Context 50 'eligible'
            $blocked = New-AcSucceededPayment $Context 100 'blocked'
            $records = @(
                (New-AcBillRecord "$($Context.Alias)-eligible" 'PAYMENT' $eligible.TransactionId 50 $eligible.OccurredAt),
                (New-AcBillRecord "$($Context.Alias)-blocked" 'PAYMENT' $blocked.TransactionId 99 $blocked.OccurredAt)
            )
            Register-AcBillAndSignal $Context $records '1' 'mixed' | Out-Null
            $prepared = New-AcPreparedSettlement $Context 'mixed'
            $detail = Get-SettlementDetail $prepared.settlementId
            $blockedLine = @($detail.lines | Where-Object paymentId -eq $blocked.PaymentId)[0]
            $eligibleLine = @($detail.lines | Where-Object paymentId -eq $eligible.PaymentId)[0]
            Assert-AcEqual $blockedLine.decision 'EXCLUDED' 'blocking difference candidate is excluded'
            Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace([string]$blockedLine.reasonCode)) -Message 'excluded candidate has stable reasonCode' -Actual $blockedLine
            Assert-AcEqual $eligibleLine.decision 'INCLUDED' 'unblocked candidate remains included'
            Add-AcObservation 'settlementLines' $detail.lines
        }
        'PAY-AC-062' {
            $reconciled = New-AcReconciledPayment $Context 100 'consumed'
            $first = New-AcPreparedSettlement $Context 'first'
            Confirm-AcSettlement $Context $first.settlementId 'first' | Out-Null
            $period2Start = ([DateTimeOffset]::Parse((Get-AcSettlementPeriod $Context.BusinessDate).end))
            $period2 = @{start=$period2Start.ToString('o');end=$period2Start.AddDays(1).ToString('o');timezone='Asia/Shanghai'}
            $secondContext = [pscustomobject]@{
                ScenarioId=$Context.ScenarioId;Alias="$($Context.Alias)-second";MerchantId=$Context.MerchantId
                ChannelId=$Context.ChannelId;BaseInstant=$Context.BaseInstant.AddDays(1)
                BusinessDate=([DateTimeOffset]::Parse($period2.start).ToOffset([TimeSpan]::FromHours(8))).ToString('yyyy-MM-dd')
                ChannelConfiguration=$Context.ChannelConfiguration
            }
            Set-AcClock $secondContext.BaseInstant | Out-Null
            $nextPeriod = New-AcReconciledPayment $secondContext 50 'next-period'
            $second = New-AcPreparedSettlement $secondContext 'second' $period2
            $secondDetail = Get-SettlementDetail $second.settlementId
            $duplicates = @($secondDetail.lines | Where-Object { $_.paymentId -eq $reconciled.Payment.PaymentId -and $_.decision -eq 'INCLUDED' })
            Assert-AcEqual $duplicates.Count 0 'confirmed source fact is not included again'
            $nextPeriodLines = @($secondDetail.lines | Where-Object { $_.paymentId -eq $nextPeriod.Payment.PaymentId -and $_.decision -eq 'INCLUDED' })
            Assert-AcEqual $nextPeriodLines.Count 1 'subsequent period still includes its own eligible source fact'
            $firstDetail = Get-SettlementDetail $first.settlementId
            Assert-Ac -Condition ([bool]$firstDetail.compositionFrozen) -Message 'first confirmed settlement retains consumed source' -Actual $firstDetail
            Add-AcObservation 'firstSettlement' $firstDetail; Add-AcObservation 'secondSettlement' $secondDetail
        }
        'PAY-AC-063' {
            $flow = New-AcExecutableSettlement $Context 127 'success'
            $amount = [decimal]$flow.Detail.netMoney.amountMinor / 100
            $first = Send-AcSettlementResult $Context $flow.Prepared.settlementId $flow.Execution $amount 'SUCCESS' 'success'
            $repeat = Invoke-AcHttp POST '/api/channel/settlement-results' @{
                channelId='C-001';notificationId="SN-$($Context.Alias)-success-SUCCESS";settlementId=$flow.Prepared.settlementId
                executionAttemptId=$flow.Execution.attemptId;executionGroupIdentity=$flow.Execution.executionGroupIdentity
                requestIdentity=$flow.Execution.requestIdentity;externalSettlementIdentity="STL-$($flow.Execution.requestIdentity)"
                money=@{currency='CNY';amountMinor=(ConvertTo-MinorString $amount)};result='SUCCESS';resultCode='SUCCESS'
                occurredAt=$Context.BaseInstant.AddHours(13).ToString('o');receivedAt=$Context.BaseInstant.AddHours(13).AddSeconds(30).ToString('o')
                rawPayload="reference-settlement-$($Context.Alias)-success-SUCCESS"
            } @(200)
            $detail = Get-SettlementDetail $flow.Prepared.settlementId
            Assert-AcEqual $detail.status 'SETTLED' 'successful execution settles the public resource'
            Assert-AcEqual $detail.finality 'FINAL' 'successful execution is final'
            Assert-Ac -Condition ([bool]$detail.settledFactFormed) -Message 'successful execution forms one settlement fact' -Actual $detail
            Assert-Ac -Condition ([bool]$repeat.Body.settledFactFormedNow -eq $false) -Message 'duplicate success forms no second fact' -Actual $repeat.Body
            Assert-Ac -Condition ($detail.attempts[0].notificationReceiveCount -ge 2) -Message 'duplicate receipt remains observable' -Actual $detail.attempts[0]
            Add-AcObservation 'settlement' (Get-SettlementDetail $flow.Prepared.settlementId)
        }
        'PAY-AC-064' {
            $flow = New-AcExecutableSettlement $Context 100 'unknown'
            $amount = [decimal]$flow.Detail.netMoney.amountMinor / 100
            Send-AcSettlementResult $Context $flow.Prepared.settlementId $flow.Execution $amount 'UNKNOWN' 'unknown' | Out-Null
            Set-AcClock $Context.BaseInstant.AddHours(14) | Out-Null
            Invoke-AcHttp POST '/api/reference-fixtures/maintenance' @{action='SETTLEMENT_UNKNOWN_REVIEW'} @(200) | Out-Null
            $retry = Invoke-AcHttp POST "/api/merchant-settlements/$($flow.Prepared.settlementId)/executions" @{
                executionChannelId='C-001';idempotencyKey="$($Context.Alias)-unknown-new-execution"
            } @(400,409)
            $detail = Get-SettlementDetail $flow.Prepared.settlementId
            $reviews = Search-ManualReviews @{merchantId=$Context.MerchantId;relatedResourceId=$flow.Prepared.settlementId;pageSize=10}
            Assert-AcEqual $detail.status 'RESULT_UNKNOWN' 'unknown settlement retains the public unknown state'
            Assert-AcEqual $detail.finality 'REVIEW_REQUIRED' 'overdue unknown settlement requires review'
            Assert-AcEqual (Get-AcErrorCode $retry.Body) 'RESULT_UNKNOWN_REEXECUTION_FORBIDDEN' 'unknown result forbids new execution identity'
            Assert-AcEqual $detail.attempts.Count 1 'unknown result retains original execution only'
            Assert-Ac -Condition ($reviews.items.Count -ge 1) -Message 'unknown result creates review item' -Actual $reviews
            Add-AcObservation 'settlement' $detail; Add-AcObservation 'manualReviews' $reviews
        }
        'PAY-AC-065' {
            $flow = New-AcExecutableSettlement $Context 100 'conflict'
            $amount = [decimal]$flow.Detail.netMoney.amountMinor / 100
            Send-AcSettlementResult $Context $flow.Prepared.settlementId $flow.Execution $amount 'SUCCESS' 'first' | Out-Null
            $failure = Send-AcSettlementResult $Context $flow.Prepared.settlementId $flow.Execution $amount 'FAILED' 'late-failure'
            $detail = Get-SettlementDetail $flow.Prepared.settlementId
            Assert-AcEqual $detail.status 'SETTLED' 'failure after success does not roll back settlement'
            Assert-AcEqual $detail.finality 'FINAL' 'conflicting failure does not roll back finality'
            $reviews = Search-ManualReviews @{merchantId=$Context.MerchantId;relatedResourceId=$flow.Prepared.settlementId;pageSize=10}
            Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace([string]$failure.conflictSummary)) -Message 'conflicting failure retains a conflict receipt' -Actual $failure
            Assert-Ac -Condition ($reviews.items.Count -ge 1) -Message 'conflicting failure creates a review' -Actual $reviews
            Assert-Ac -Condition ([bool]$detail.settledFactFormed) -Message 'settled fact remains formed' -Actual $detail
            Add-AcObservation 'settlement' $detail
        }
        'PAY-AC-066' {
            Invoke-AcHttp POST '/api/reference-fixtures/policy' @{feeRate=0.30;roundingMode='HALF_UP';currencyPrecisions=@{CNY=2}} @(200) | Out-Null
            $payment = New-AcSucceededPayment $Context 100 'negative-source'
            $refund = New-AcSucceededRefund $Context $payment.PaymentId 100 'negative-refund'
            $records = @(
                (New-AcBillRecord "$($Context.Alias)-p" 'PAYMENT' $payment.TransactionId 100 $payment.OccurredAt),
                (New-AcBillRecord "$($Context.Alias)-r" 'REFUND' $refund.ChannelRefundId 100 $refund.OccurredAt)
            )
            Register-AcBillAndSignal $Context $records '1' 'negative' | Out-Null
            $period = Get-AcSettlementPeriod $Context.BusinessDate
            $prepared = New-AcPreparedSettlement $Context 'negative' $period
            $detail = Get-SettlementDetail $prepared.settlementId
            $execute = Invoke-AcHttp POST "/api/merchant-settlements/$($prepared.settlementId)/executions" @{
                executionChannelId='C-001';idempotencyKey="$($Context.Alias)-negative-execute"
            } @(400,409)
            Assert-AcEqual ([decimal]$payment.Detail.feeSnapshot.feeRate) ([decimal]0.30) 'payment freezes the scenario policy fee rate used by the negative-net fixture'
            Assert-AcEqual $payment.Detail.feeSnapshot.roundingMode 'HALF_UP' 'payment freezes the scenario policy rounding mode'
            Assert-AcEqual $payment.Detail.feeSnapshot.currencyPrecision 2 'payment freezes the scenario currency precision'
            Assert-AcEqual $detail.grossMoney.amountMinor '10000' 'negative settlement preserves 100.00 payment gross'
            Assert-AcEqual $detail.refundMoney.amountMinor '10000' 'negative settlement preserves 100.00 refund deduction'
            Assert-AcEqual $detail.feeMoney.amountMinor '3000' 'negative settlement preserves the 30.00 frozen payment fee'
            Assert-AcEqual $detail.adjustmentMoney.amountMinor '0' 'negative fixture has no adjustment component'
            Assert-AcEqual $detail.netMoney.amountMinor '-3000' 'period net amount is exactly -30.00'
            Assert-AcEqual $detail.status 'READY_FOR_CONFIRMATION' 'negative settlement remains at the public confirmation boundary'
            Assert-AcEqual $detail.finality 'REVIEW_REQUIRED' 'negative settlement requires review'
            Assert-AcEqual (Get-AcErrorCode $execute.Body) 'VALIDATION_ERROR' 'negative settlement rejects execution synchronously'
            $reviews = Search-ManualReviews @{merchantId=$Context.MerchantId;relatedResourceId=$prepared.settlementId;pageSize=10}
            Assert-Ac -Condition ($reviews.items.Count -ge 1) -Message 'negative settlement creates a manual review' -Actual $reviews
            Assert-AcEqual $detail.attempts.Count 0 'negative settlement has no transfer attempt'
            Assert-Ac -Condition (@($detail.lines | Where-Object decision -eq 'INCLUDED').Count -ge 2) -Message 'negative settlement retains the included payment and refund amount components' -Actual $detail.lines
            Add-AcObservation 'settlement' $detail
        }
        'PAY-AC-067' {
            $reconciled = New-AcReconciledPayment $Context 100 'freeze'
            $prepared = New-AcPreparedSettlement $Context 'freeze'
            Confirm-AcSettlement $Context $prepared.settlementId 'freeze' | Out-Null
            $frozen = Get-SettlementDetail $prepared.settlementId
            New-AcSucceededRefund $Context $reconciled.Payment.PaymentId 20 'late-refund' | Out-Null
            $after = Get-SettlementDetail $prepared.settlementId
            Assert-Ac -Condition ([bool]$after.compositionFrozen) -Message 'confirmed composition remains frozen' -Actual $after.compositionFrozen
            Assert-AcEqual $after.periodStart $frozen.periodStart 'confirmed period start is immutable'
            Assert-AcEqual $after.periodEnd $frozen.periodEnd 'confirmed period end is immutable'
            Assert-AcEqual $after.netMoney.amountMinor $frozen.netMoney.amountMinor 'late refund does not rewrite frozen Money'
            Assert-AcEqual $after.lines.Count $frozen.lines.Count 'late refund does not rewrite frozen items'
            Add-AcObservation 'frozenSettlement' $after
        }
        'PAY-AC-068' {
            New-AcReconciledPayment $Context 100 'scope' | Out-Null
            $period = Get-AcSettlementPeriod $Context.BusinessDate
            $first = New-AcPreparedSettlement $Context 'scope1' $period
            $conflict = Invoke-AcHttp POST '/api/merchant-settlements' @{
                merchantId=$Context.MerchantId;currency='CNY';settlementPeriod=$period;idempotencyKey="$($Context.Alias)-scope2"
            } @(400,409)
            Assert-AcEqual (Get-AcErrorCode $conflict.Body) 'SETTLEMENT_SCOPE_CONFLICT' 'same merchant/currency/period rejects second active settlement'
            $voided = Invoke-AcHttp POST "/api/merchant-settlements/$($first.settlementId)/voids" @{
                reason='replace invalid composition';evidence='evidence://replacement';idempotencyKey="$($Context.Alias)-void";createReplacement=$true
            } @(200) 'fixture-settlement-operator'
            $original = Get-SettlementDetail $first.settlementId
            $replacement = Get-SettlementDetail $voided.Body.replacementSettlementId
            Assert-AcEqual $original.replacementSettlementId $replacement.settlementId 'voided settlement points to replacement'
            Assert-AcEqual $replacement.predecessorSettlementId $original.settlementId 'replacement points back to voided settlement'
            Assert-AcEqual $voided.Body.actorId 'reference-settlement-operator' 'void/replacement records trusted actor'
            Add-AcObservation 'voidedSettlement' $original; Add-AcObservation 'replacementSettlement' $replacement
        }
        default { throw "Unknown settlement scenario $Id" }
    }
}

function Invoke-ScopeNotificationTracePayAcScenario {
    param([string]$Id, $Context)
    switch ($Id) {
        'PAY-AC-080' {
            $other = New-AcChildContext $Context 'merchant-b' 0
            $a = New-AcSucceededPayment $Context 100 'merchant-a'
            $b = New-AcPayment $other 50 'CNY' 'merchant-b' 'merchant-b'
            $listA = (Invoke-AcHttp POST '/api/payments/search' @{merchantId=$Context.MerchantId;pageSize=20} @(200)).Body
            $before = Get-PaymentDetail $a.PaymentId
            $cross = Invoke-AcHttp POST '/api/refunds' @{
                merchantId=$other.MerchantId;idempotencyKey="$($Context.Alias)-cross";merchantRefundNo="$($Context.Alias)-cross"
                paymentId=$a.PaymentId;money=@{currency='CNY';amountMinor='1000'};reason='cross merchant request'
            } @(400,409)
            $after = Get-PaymentDetail $a.PaymentId
            Assert-Ac -Condition (@($listA.items | Where-Object merchantId -ne $Context.MerchantId).Count -eq 0) -Message 'merchant filter returns only requested merchant' -Actual $listA
            Assert-Ac -Condition (@($listA.items | Where-Object paymentId -eq $b.paymentId).Count -eq 0) -Message 'merchant A list excludes merchant B payment' -Actual $listA
            Assert-AcEqual (Get-AcErrorCode $cross.Body) 'VALIDATION_ERROR' 'cross-merchant refund is rejected'
            Assert-AcEqual $after.refundBudget.availableAmount.amountMinor $before.refundBudget.availableAmount.amountMinor 'cross-merchant rejection has no budget side effect'
            Add-AcObservation 'merchantAPayments' $listA; Add-AcObservation 'paymentBudget' $after
        }
        'PAY-AC-081' {
            $created = New-AcPayment $Context 100 'CNY' 'order-notify' 'create-notify' $Context.BaseInstant.AddMinutes(30)
            $sourceFactIdentity = "payment:$($created.paymentId):merchant-success:v1"
            Invoke-AcHttp POST '/api/reference-fixtures/merchant-notification-sender-script' @{sourceKind='PAYMENT';sourceFactIdentity=$sourceFactIdentity;script='FAILURE'} @(200) | Out-Null
            $attempt = New-AcPaymentAttempt $Context $created.paymentId 'notify'
            Submit-AcPaymentAttempt $Context $created.paymentId $attempt.paymentAttemptId 'notify' | Out-Null
            Send-AcPaymentResult $Context $created.paymentId $attempt.paymentAttemptId 100 'SUCCESS' 'notify' $true | Out-Null
            $payment = [pscustomobject]@{PaymentId=$created.paymentId;Detail=(Get-PaymentDetail $created.paymentId)}
            $listed = Wait-AcNotification @{merchantId=$Context.MerchantId;paymentId=$payment.PaymentId;sourceKind='PAYMENT';pageSize=10}
            Assert-AcEqual $listed.items.Count 1 'payment success creates one stable notification'
            $notificationId = $listed.items[0].notificationId
            $failed = (Invoke-AcHttp GET "/api/merchant-notifications/$notificationId" $null @(200)).Body.notification
            Invoke-AcHttp POST '/api/reference-fixtures/merchant-notification-sender-script' @{notificationIdentity=$failed.notificationIdentity;script='SUCCESS'} @(200) | Out-Null
            Invoke-AcHttp POST "/api/merchant-notifications/$notificationId/retries" @{idempotencyKey="$($Context.Alias)-retry-notification"} @(200) | Out-Null
            $delivered = (Invoke-AcHttp GET "/api/merchant-notifications/$notificationId" $null @(200)).Body.notification
            Assert-AcEqual $failed.status 'FAILED' 'first delivery is failed by fixture'
            Assert-AcEqual $delivered.status 'DELIVERED' 'retry succeeds'
            Assert-AcEqual $delivered.notificationIdentity $failed.notificationIdentity 'retry preserves notification identity'
            Assert-AcEqual $delivered.contentIdentity $failed.contentIdentity 'retry preserves content identity'
            Assert-AcEqual $delivered.deliveryAttempts.Count 2 'both delivery attempts remain visible'
            Assert-Ac -Condition ([bool](Get-PaymentDetail $payment.PaymentId).successFactFormed) -Message 'notification retry does not duplicate or roll back payment fact' -Actual $payment.Detail
            Add-AcObservation 'merchantNotification' $delivered
        }
        'PAY-AC-082' {
            $record = New-AcBillRecord "$($Context.Alias)-omission" 'PAYMENT' "CT-OMITTED-$($Context.Alias)" 100 $Context.BaseInstant.AddMinutes(1).ToString('o')
            $bill = Register-AcBillAndSignal $Context @($record) '1' 'omission'
            $item = @($bill.Run.differences | Where-Object differenceType -eq 'CHANNEL_ONLY')[0]
            $confirmed = Invoke-AcHttp POST "/api/reconciliation-runs/$($bill.Signal.runId)/differences/$($item.itemId)/confirmations" @{
                merchantId=$Context.MerchantId;channelId='C-001';reason='verified omitted platform fact';evidence='evidence://omitted-success';idempotencyKey="$($Context.Alias)-omission-confirm"
            } @(200) 'fixture-reconciliation-operator'
            $after = (Invoke-AcHttp GET "/api/reconciliation-runs/$($bill.Signal.runId)" $null @(200)).Body
            $history = @($after.differences | Where-Object itemId -eq $item.itemId)[0]
            Assert-AcEqual $history.differenceType 'CHANNEL_ONLY' 'original channel-only difference remains visible'
            Assert-AcEqual $history.channelTransactionIdentity "CT-OMITTED-$($Context.Alias)" 'original channel evidence remains visible'
            Assert-AcEqual $history.confirmationFacts.Count 1 'correction is an appended confirmation fact'
            Assert-AcEqual $confirmed.Body.actorId 'reference-reconciliation-operator' 'correction records trusted actor'
            Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace([string]$confirmed.Body.recordedAt)) -Message 'correction records server time' -Actual $confirmed.Body.recordedAt
            Add-AcObservation 'factCorrectionHistory' $history
        }
        'PAY-AC-083' {
            $created = New-AcPayment $Context 100 'CNY' 'order-timeline' 'timeline'
            $createReplay = New-AcPayment $Context 100 'CNY' 'order-timeline' 'timeline'
            Invoke-AcHttp POST '/api/reference-fixtures/payment-channel-script' @{channelId='C-001';script='REJECT_ON_SUBMIT'} @(200) | Out-Null
            $attempt1 = New-AcPaymentAttempt $Context $created.paymentId 'timeline-1'
            $submission1 = Submit-AcPaymentAttempt $Context $created.paymentId $attempt1.paymentAttemptId 'timeline-1'
            Invoke-AcHttp POST '/api/reference-fixtures/payment-channel-script/reset' @{channelId='C-001'} @(200) | Out-Null
            $attempt2 = New-AcPaymentAttempt $Context $created.paymentId 'timeline-2'
            $submission2 = Submit-AcPaymentAttempt $Context $created.paymentId $attempt2.paymentAttemptId 'timeline-2'
            $successfulResult = Send-AcPaymentResult $Context $created.paymentId $attempt2.paymentAttemptId 100 'SUCCESS' 'timeline-2' $true
            $paymentDetail = Get-PaymentDetail $created.paymentId
            $refund = New-AcSucceededRefund $Context $created.paymentId 20 'timeline'

            $revision1Records = @(
                (New-AcBillRecord "$($Context.Alias)-timeline-payment-r1" 'PAYMENT' $successfulResult.TransactionId 99 $successfulResult.OccurredAt),
                (New-AcBillRecord "$($Context.Alias)-timeline-refund-r1" 'REFUND' $refund.ChannelRefundId 20 $refund.OccurredAt)
            )
            $bill = Register-AcBillAndSignal $Context $revision1Records '1' 'timeline'
            $item = @($bill.Run.differences | Where-Object differenceType -eq 'AMOUNT_MISMATCH')[0]
            $disposition = (Invoke-AcHttp POST "/api/reconciliation-runs/$($bill.Signal.runId)/differences/$($item.itemId)/dispositions" @{
                merchantId=$Context.MerchantId;channelId='C-001';conclusion='NO_SETTLEMENT_IMPACT';settlementImpact='DOES_NOT_BLOCK_SETTLEMENT';reason='timeline disposition';evidence='evidence://timeline';idempotencyKey="$($Context.Alias)-timeline-dispose"
            } @(200) 'fixture-reconciliation-operator').Body

            $revision2Records = @(
                (New-AcBillRecord "$($Context.Alias)-timeline-payment-r2" 'PAYMENT' $successfulResult.TransactionId 100 $successfulResult.OccurredAt),
                (New-AcBillRecord "$($Context.Alias)-timeline-refund-r2" 'REFUND' $refund.ChannelRefundId 20 $refund.OccurredAt)
            )
            $registered2 = (Invoke-AcHttp POST '/api/reference-fixtures/bills' @{
                channelId='C-001';billIdentity=$bill.BillIdentity;businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai'
                revision='2';completeness='COMPLETE';rawEvidence="evidence://$($bill.BillIdentity)/2";payloadFingerprint="$($bill.BillIdentity)-timeline-r2"
                publishedAt=$Context.BaseInstant.AddHours(13).ToString('o');records=$revision2Records
            } @(200)).Body
            $signal2 = (Invoke-AcHttp POST "/api/reference-fixtures/bills/$($bill.BillIdentity)/signals" @{
                channelId='C-001';businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai'
                signalIdentity="SIGNAL-$($bill.BillIdentity)-2";announcedRevision='2';publishedAt=$Context.BaseInstant.AddHours(13).ToString('o')
            } @(200)).Body
            $revision2Run = (Invoke-AcHttp GET "/api/reconciliation-runs/$($signal2.runId)" $null @(200)).Body

            $settlement = New-AcPreparedSettlement $Context 'timeline'
            $voided = (Invoke-AcHttp POST "/api/merchant-settlements/$($settlement.settlementId)/voids" @{
                reason='replace timeline settlement before execution';evidence='evidence://timeline/replacement';idempotencyKey="$($Context.Alias)-timeline-void";createReplacement=$true
            } @(200) 'fixture-settlement-operator').Body
            $replacementId = $voided.replacementSettlementId
            Confirm-AcSettlement $Context $replacementId 'timeline-replacement' | Out-Null
            $execution = Start-AcSettlementExecution $Context $replacementId 'timeline-replacement'
            $replacementBeforeResult = Get-SettlementDetail $replacementId
            Send-AcSettlementResult $Context $replacementId $execution ([decimal]$replacementBeforeResult.netMoney.amountMinor/100) 'SUCCESS' 'timeline-replacement' | Out-Null
            $replacement = Get-SettlementDetail $replacementId

            $timeline = (Invoke-AcHttp GET "/api/payments/$($created.paymentId)/timeline?pageSize=100" $null @(200)).Body
            $types = @($timeline.items | ForEach-Object eventType)
            foreach ($required in @(
                'PAYMENT_CREATED','OPERATION_ACCEPTED','PAYMENT_ATTEMPT_CREATED','PAYMENT_ATTEMPT_SUBMITTED','PAYMENT_ATTEMPT_ACCEPTED','PAYMENT_SUBMISSION_RECEIPT',
                'PAYMENT_RESULT_RECEIPT','PAYMENT_ATTEMPT_TERMINAL','PAYMENT_SUCCEEDED','REFUND_REQUESTED','REFUND_BUDGET_RESERVED',
                'REFUND_ATTEMPT_CREATED','REFUND_ATTEMPT_ACCEPTED','REFUND_RESULT_RECEIPT','REFUND_ATTEMPT_RESULT','REFUND_BUDGET_CONVERTED',
                'MERCHANT_NOTIFICATION','MERCHANT_NOTIFICATION_DELIVERY','AUTHORITATIVE_BILL','BILL_REVISION','BILL_AVAILABLE',
                'BILL_REVISION_RECORD','RECONCILIATION_RUN','RECONCILIATION_ITEM','RECONCILIATION_DISPOSITION','SETTLEMENT_ITEM',
                'SETTLEMENT_PREPARED','SETTLEMENT_VOIDED','SETTLEMENT_REPLACEMENT','SETTLEMENT_CONFIRMED','SETTLEMENT_EXECUTION',
                'SETTLEMENT_RESULT_RECEIPT','SETTLEMENT_SUCCEEDED'
            )) {
                Assert-Ac -Condition ($types -contains $required) -Message "timeline contains $required" -Actual $types
            }
            Assert-AcEqual $createReplay.paymentId $created.paymentId 'payment idempotency replay resolves to the original payment'
            Assert-AcEqual $createReplay.receipt.operationId $created.receipt.operationId 'payment idempotency replay resolves to the original Operation'
            Assert-AcEqual $createReplay.receipt.acceptanceStatus 'ALREADY_ACCEPTED' 'payment idempotency replay is observable without a second create effect'
            Assert-AcEqual @($paymentDetail.attempts).Count 2 'payment trace exposes two independent attempts'
            Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace([string]$submission1.submissionIdentity) -and -not [string]::IsNullOrWhiteSpace([string]$submission2.submissionIdentity)) -Message 'both channel submissions expose stable identities' -Actual @($submission1,$submission2)
            Assert-AcEqual $submission1.submissionOutcome 'REJECTED' 'first attempt retains the channel submission rejection receipt'
            Assert-AcEqual $submission2.submissionOutcome 'ACCEPTED' 'second attempt retains the channel submission acceptance receipt'
            Assert-AcEqual $successfulResult.Body.disposition 'ACCEPTED' 'second attempt success forms the accepted payment fact'
            Assert-AcEqual $paymentDetail.status 'SUCCEEDED' 'two-attempt payment converges to success'
            Assert-AcEqual $refund.Payment.refundBudget.succeededAmount.amountMinor '2000' 'partial refund budget conversion is visible'
            Assert-AcEqual $registered2.currentRevision '2' 'timeline fixture advances the authoritative bill revision'
            Assert-AcEqual $revision2Run.differenceCount 0 'corrected revision supplies a settlement-eligible current run'
            Assert-AcEqual $disposition.actorId 'reference-reconciliation-operator' 'difference disposition records the trusted actor'
            Assert-AcEqual $voided.actorId 'reference-settlement-operator' 'settlement void/replacement records the trusted actor'
            Assert-AcEqual $replacement.predecessorSettlementId $settlement.settlementId 'replacement trace points to the voided settlement'
            Assert-AcEqual $replacement.status 'SETTLED' 'replacement proceeds through execution to settlement success'

            Assert-AcEqual @($timeline.items | Where-Object eventType -eq 'PAYMENT_ATTEMPT_CREATED').Count 2 'timeline contains both payment attempt creations'
            Assert-AcEqual @($timeline.items | Where-Object eventType -eq 'PAYMENT_SUBMISSION_RECEIPT').Count 2 'timeline contains both channel submission receipts'
            Assert-Ac -Condition (@($timeline.items | Where-Object eventType -eq 'PAYMENT_RESULT_RECEIPT').Count -ge 1) -Message 'timeline contains the accepted payment result reception' -Actual $timeline.items
            Assert-Ac -Condition (@($timeline.items | Where-Object eventType -eq 'BILL_REVISION').Count -ge 2) -Message 'timeline contains the bill revision chain' -Actual $timeline.items
            Assert-AcEqual @($timeline.items | Where-Object { $_.eventType -eq 'OPERATION_ACCEPTED' -and $_.payload.identity -eq "$($Context.Alias)-timeline" }).Count 1 'idempotent payment create is represented by one accepted Operation fact'
            $successReceipt = @($timeline.items | Where-Object { $_.eventType -eq 'PAYMENT_RESULT_RECEIPT' -and $_.outcome -eq 'SUCCESS' })[0]
            Assert-AcEqual $successReceipt.money.amountMinor '10000' 'payment result receipt amount reconciles to the payment Money'
            $timelineOccurredAt = $successReceipt.occurredAt.ToUniversalTime()
            $callbackOccurredAt = [DateTimeOffset]::Parse([string]$successfulResult.OccurredAt)
            $convertedBudget = @($timeline.items | Where-Object eventType -eq 'REFUND_BUDGET_CONVERTED')[0]
            Assert-AcEqual $convertedBudget.money.amountMinor '2000' 'timeline budget conversion reconciles to the partial refund amount'
            $dispositionEvent = @($timeline.items | Where-Object eventType -eq 'RECONCILIATION_DISPOSITION')[0]
            Assert-AcEqual $dispositionEvent.actorId 'reference-reconciliation-operator' 'timeline exposes the reconciliation actor'
            Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace([string]$dispositionEvent.reason) -and $dispositionEvent.evidenceRefs.Count -ge 1) -Message 'timeline exposes disposition reason and evidence' -Actual $dispositionEvent
            Assert-Ac -Condition (@($timeline.items | Where-Object { $_.eventType -eq 'SETTLEMENT_ITEM' -and $_.refs.paymentId -eq $created.paymentId }).Count -ge 1) -Message 'settlement lines retain the payment association' -Actual $timeline.items
            Assert-Ac -Condition (@($timeline.items | Where-Object { $_.eventType -eq 'SETTLEMENT_ITEM' -and $_.refs.paymentId -eq $created.paymentId -and -not [string]::IsNullOrWhiteSpace([string]$_.payload.identity) }).Count -ge 1) -Message 'settlement line exposes its source fact identity' -Actual $timeline.items

            $keys = @($timeline.items | ForEach-Object { "$([DateTimeOffset]::Parse([string]$_.recordedAt).ToUniversalTime().ToString('o'))|$($_.eventId)" })
            $sorted = @($keys)
            [Array]::Sort($sorted, [StringComparer]::Ordinal)
            Assert-Ac -Condition (($keys -join ',') -eq ($sorted -join ',')) -Message 'timeline is ordered by recordedAt ASC,eventId ASC' -Actual $keys
            Assert-AcEqual (@($timeline.items.eventId | Select-Object -Unique).Count) $timeline.items.Count 'timeline eventId values are stable and unique'
            Add-AcObservation 'paymentTimeline' $timeline
            Assert-AcEqual $timelineOccurredAt.Ticks $callbackOccurredAt.UtcDateTime.Ticks 'external payment occurredAt is preserved independently from recordedAt'
        }
        'PAY-AC-084' {
            $history = New-AcSucceededPayment $Context 100 'history'
            $before = Get-PaymentDetail $history.PaymentId
            Invoke-AcHttp POST '/api/reference-fixtures/merchant-channels' @{
                merchantId=$Context.MerchantId;channelId='C-001';currency='CNY';paymentMethod='CARD';status='RETIRED';minimumAmount=0.01;maximumAmount=1000000.00;routingPriority=10
                refundWindowDays=30;refundResultReviewAfterMinutes=5;settlementFeeBasisPoints=60;settlementFixedFeeAmount=0;settlementFeeRoundingMode='HALF_UP';settlementResultReviewAfterMinutes=5
            } @(200) | Out-Null
            $newPayment = Invoke-AcHttp POST '/api/payments' @{
                merchantId=$Context.MerchantId;merchantOrderNumber="$($Context.Alias)-after-retire";idempotencyKey="$($Context.Alias)-after-retire";money=@{currency='CNY';amountMinor='10000'};paymentMethod='CARD'
            } @(400,409)
            $after = Get-PaymentDetail $history.PaymentId
            Assert-AcEqual (Get-AcErrorCode $newPayment.Body) 'NO_ELIGIBLE_CHANNEL' 'retired channel is unavailable for new payment'
            Assert-AcEqual $after.attempts[0].channelId 'C-001' 'historical attempt retains channel identity'
            Assert-AcEqual $after.feeSnapshot.feeRate $before.feeSnapshot.feeRate 'historical fee snapshot remains unchanged'
            Add-AcObservation 'historicalPayment' $after
        }
        'PAY-AC-085' {
            $beforeBoundary = [DateTimeOffset]::Parse("$($Context.BusinessDate)T23:59:00+08:00")
            $nextDate = ([datetime]::ParseExact($Context.BusinessDate,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)).AddDays(1).ToString('yyyy-MM-dd')
            $afterBoundary = [DateTimeOffset]::Parse("${nextDate}T00:01:00+08:00")
            $p1 = New-AcPayment $Context 10 'CNY' '2359' '2359' $beforeBoundary.AddHours(1)
            $a1 = New-AcPaymentAttempt $Context $p1.paymentId '2359'; Submit-AcPaymentAttempt $Context $p1.paymentId $a1.paymentAttemptId '2359' | Out-Null
            $r1 = Send-AcPaymentResult $Context $p1.paymentId $a1.paymentAttemptId 10 'SUCCESS' '2359' $true $null $beforeBoundary
            Set-AcClock $afterBoundary.AddMinutes(-2) | Out-Null
            $child = New-AcChildContext $Context 'next-day' 1; $child.BaseInstant = $afterBoundary.AddMinutes(-2); $child.BusinessDate = $nextDate
            $p2 = New-AcPayment $child 20 'CNY' '0001' '0001' $afterBoundary.AddHours(1)
            $a2 = New-AcPaymentAttempt $child $p2.paymentId '0001'; Submit-AcPaymentAttempt $child $p2.paymentId $a2.paymentAttemptId '0001' | Out-Null
            $r2 = Send-AcPaymentResult $child $p2.paymentId $a2.paymentAttemptId 20 'SUCCESS' '0001' $true $null $afterBoundary
            $bill1 = Register-AcBillAndSignal $Context @((New-AcBillRecord "$($Context.Alias)-2359" 'PAYMENT' $r1.TransactionId 10 $beforeBoundary.ToString('o'))) '1' 'day1'
            $bill2 = Register-AcBillAndSignal $child @((New-AcBillRecord "$($Context.Alias)-0001" 'PAYMENT' $r2.TransactionId 20 $afterBoundary.ToString('o'))) '1' 'day2'
            Assert-AcEqual $bill1.Run.businessDate $Context.BusinessDate '23:59 fact belongs to first Asia/Shanghai business date'
            Assert-AcEqual $bill2.Run.businessDate $nextDate '00:01 fact belongs to next Asia/Shanghai business date'
            Assert-AcEqual $bill1.Run.businessTimezone 'Asia/Shanghai' 'run preserves business timezone'
            Assert-Ac -Condition ($bill1.Run.matchedCount -eq 1 -and $bill1.Run.differenceCount -eq 0 -and -not [bool]$bill1.Run.settlementBlocked) -Message 'first boundary fact reconciles in its date' -Actual $bill1.Run
            Assert-Ac -Condition ($bill2.Run.matchedCount -eq 1 -and $bill2.Run.differenceCount -eq 0 -and -not [bool]$bill2.Run.settlementBlocked) -Message 'second boundary fact reconciles in its date' -Actual $bill2.Run
            Add-AcObservation 'day1Run' $bill1.Run; Add-AcObservation 'day2Run' $bill2.Run
        }
        'PAY-AC-086' {
            $created = New-AcPayment $Context 100 'CNY' 'actor' 'actor' $Context.BaseInstant.AddMinutes(2)
            Invoke-AcHttp POST '/api/reference-fixtures/payment-channel-script' @{channelId='C-001';script='REJECT_ON_SUBMIT'} @(200) | Out-Null
            $attempt = New-AcPaymentAttempt $Context $created.paymentId 'actor'; Submit-AcPaymentAttempt $Context $created.paymentId $attempt.paymentAttemptId 'actor' | Out-Null
            Set-AcClock $Context.BaseInstant.AddMinutes(3) | Out-Null; Invoke-AcHttp POST '/api/reference-fixtures/maintenance' @{action='PAYMENT_EXPIRY'} @(200) | Out-Null
            Send-AcPaymentResult $Context $created.paymentId $attempt.paymentAttemptId 100 'SUCCESS' 'actor' $true | Out-Null
            $payment = Get-PaymentDetail $created.paymentId; $reviewId = $payment.reviews[0].reviewId
            $path = "/api/payments/$($created.paymentId)/reviews/$reviewId/decisions"
            $body = @{merchantId=$Context.MerchantId;idempotencyKey="$($Context.Alias)-decision";decisionIdentity="$($Context.Alias)-decision";decision='KEEP_CURRENT_TERMINAL';reason='merchant order replaced';evidence='ticket://late/1';eligibilityImpact='ALLOW_SETTLEMENT';remediationReference=$null}
            $missing = Invoke-AcHttp POST $path $body @(400)
            $unknownActor = Invoke-AcHttp POST $path $body @(400) 'unknown-reference-actor'
            $incompleteBody = $body.Clone(); $incompleteBody['reason'] = ''
            $incomplete = Invoke-AcHttp POST $path $incompleteBody @(400) 'fixture-payment-reviewer'
            $valid = Invoke-AcHttp POST $path $body @(200) 'fixture-payment-reviewer'
            $after = Get-PaymentDetail $created.paymentId
            Assert-AcEqual (Get-AcErrorCode $missing.Body) 'VALIDATION_ERROR' 'missing actor context rejects synchronously'
            Assert-AcEqual (Get-AcErrorCode $unknownActor.Body) 'VALIDATION_ERROR' 'unknown actor context rejects synchronously'
            Assert-AcEqual (Get-AcErrorCode $incomplete.Body) 'VALIDATION_ERROR' 'missing responsibility field rejects synchronously'
            Assert-Ac -Condition (($missing.Body.PSObject.Properties.Name -notcontains 'receipt') -and ($unknownActor.Body.PSObject.Properties.Name -notcontains 'receipt') -and ($incomplete.Body.PSObject.Properties.Name -notcontains 'receipt')) -Message 'invalid actor or responsibility input creates no Operation receipt' -Actual @($missing.Body,$unknownActor.Body,$incomplete.Body)
            Assert-AcEqual $valid.Body.actorId 'reference-payment-reviewer' 'valid decision exposes trusted registry actorId'
            Assert-AcEqual $valid.Body.receipt.acceptanceStatus 'ACCEPTED' 'valid actor is accepted after invalid attempts with the same key'
            Assert-AcEqual $after.reviews[0].decisions.Count 1 'invalid requests produce no decision side effect'
            Assert-AcEqual $after.reviews[0].decisions[0].reason 'merchant order replaced' 'responsibility reason is persisted'
            Add-AcObservation 'paymentReview' $after.reviews[0]
        }
        'PAY-AC-087' {
            $payment = New-AcSucceededPayment $Context 100 'bill'
            $record = New-AcBillRecord "$($Context.Alias)-bill-record" 'PAYMENT' $payment.TransactionId 100 $payment.OccurredAt
            $rev1 = Register-AcBillAndSignal $Context @($record) '1' 'authority' 1
            $retry = Invoke-AcHttp POST "/api/reference-fixtures/bills/$($rev1.BillIdentity)/signals" @{
                channelId='C-001';businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai';signalIdentity="SIGNAL-$($rev1.BillIdentity)-1";announcedRevision='1';publishedAt=$Context.BaseInstant.AddHours(12).ToString('o')
            } @(200)
            $signalReplay = Invoke-AcHttp POST "/api/reference-fixtures/bills/$($rev1.BillIdentity)/signals" @{
                channelId='C-001';businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai';signalIdentity="SIGNAL-$($rev1.BillIdentity)-1";announcedRevision='1';publishedAt=$Context.BaseInstant.AddHours(12).ToString('o')
            } @(200)
            $manualRerun1 = Invoke-AcHttp POST "/api/reconciliation-runs/$($retry.Body.runId)/reruns" @{idempotencyKey="$($Context.Alias)-authority-rerun-r1"} @(200) 'fixture-reconciliation-operator'
            $rev2Registered = (Invoke-AcHttp POST '/api/reference-fixtures/bills' @{
                channelId='C-001';billIdentity=$rev1.BillIdentity;businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai';revision='2';completeness='COMPLETE';rawEvidence="evidence://$($rev1.BillIdentity)/2";payloadFingerprint="$($rev1.BillIdentity)-2-fingerprint";publishedAt=$Context.BaseInstant.AddHours(13).ToString('o');records=@($record)
            } @(200)).Body
            $rev2Signal = (Invoke-AcHttp POST "/api/reference-fixtures/bills/$($rev1.BillIdentity)/signals" @{
                channelId='C-001';businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai';signalIdentity="SIGNAL-$($rev1.BillIdentity)-2";announcedRevision='2';publishedAt=$Context.BaseInstant.AddHours(13).ToString('o')
            } @(200)).Body
            $manualRerun2 = Invoke-AcHttp POST "/api/reconciliation-runs/$($rev2Signal.runId)/reruns" @{idempotencyKey="$($Context.Alias)-authority-rerun-r2"} @(200) 'fixture-reconciliation-operator'
            $late = (Invoke-AcHttp POST "/api/reference-fixtures/bills/$($rev1.BillIdentity)/signals" @{
                channelId='C-001';businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai';signalIdentity="$($Context.Alias)-late-rev1";announcedRevision='1';publishedAt=$Context.BaseInstant.AddHours(14).ToString('o')
            } @(200)).Body
            $runs = (Invoke-AcHttp POST '/api/reconciliation-runs/search' @{merchantId=$Context.MerchantId;billId=$rev1.Registered.billId;pageSize=20} @(200)).Body
            Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace([string]$rev1.Signal.diagnostic)) -Message 'temporarily unavailable bill is diagnostic' -Actual $rev1.Signal
            Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace([string]$retry.Body.runId)) -Message 'same stable signal recovers to one run' -Actual $retry.Body
            Assert-AcEqual $signalReplay.Body.runId $retry.Body.runId 'same stable signal identity reuses the recovered revision run'
            Assert-Ac -Condition ([bool]$signalReplay.Body.idempotentReplay) -Message 'signal retransmission is explicitly observable as an idempotent replay' -Actual $signalReplay.Body
            Assert-AcEqual $manualRerun1.Body.runId $retry.Body.runId 'manual rerun of the same bill revision reuses its run'
            Assert-Ac -Condition ([bool]$manualRerun1.Body.idempotentReplay) -Message 'manual rerun reports aggregate-level bill revision replay' -Actual $manualRerun1.Body
            Assert-AcEqual $rev2Registered.currentRevision '2' 'higher revision becomes current'
            Assert-AcEqual $late.runId $retry.Body.runId 'late lower revision replays its original revision run'
            Assert-Ac -Condition ([bool]$late.idempotentReplay) -Message 'late lower revision signal is an idempotent replay' -Actual $late
            $revision1Runs = @($runs.items | Where-Object billRevision -eq '1')
            $revision2Runs = @($runs.items | Where-Object billRevision -eq '2')
            Assert-AcEqual $revision1Runs.Count 1 'scheduler/signal/manual inputs form one revision-one run'
            Assert-AcEqual $revision2Runs.Count 1 'scheduler/signal/manual inputs form one revision-two run'
            Assert-AcEqual @($revision1Runs | Where-Object effectiveRun).Count 0 'obsolete revision has no effective run after revision two'
            Assert-AcEqual @($revision2Runs | Where-Object effectiveRun).Count 1 'same bill and current revision has one effective run'
            $effectiveRuns = @($runs.items | Where-Object effectiveRun)
            Assert-AcEqual $manualRerun2.Body.runId $rev2Signal.runId 'manual rerun of revision two reuses its effective run'
            Assert-Ac -Condition ([bool]$manualRerun2.Body.idempotentReplay) -Message 'revision-two manual rerun reports aggregate-level replay' -Actual $manualRerun2.Body
            Assert-Ac -Condition ($effectiveRuns.Count -eq 1 -and $effectiveRuns[0].runId -eq $rev2Signal.runId) -Message 'manual rerun and late lower revision do not displace the current run' -Actual $runs
            Add-AcObservation 'reconciliationRuns' $runs

            # PAY-AC-087 explicitly includes the ordinary daily scheduler trigger.  This remains a
            # strict black-box requirement: signal/rerun coverage must not be relabelled as scheduler
            # coverage when the reference HTTP fixture has no scheduler entry point.
            Set-AcClock $Context.BaseInstant.AddDays(1) | Out-Null
            $schedulerTrigger = Invoke-AcHttp POST '/api/reference-fixtures/maintenance' @{action='RECONCILIATION_DAILY'} @(200,400)
            if ($schedulerTrigger.StatusCode -ne 200) { Get-AcErrorCode $schedulerTrigger.Body | Out-Null }
            Add-AcObservation 'reconciliationSchedulerTrigger' $schedulerTrigger.Body
            Assert-AcEqual $schedulerTrigger.StatusCode 200 'reference HTTP exposes the reconciliation daily scheduler trigger required by PAY-AC-087'
            $afterScheduler = (Invoke-AcHttp POST '/api/reconciliation-runs/search' @{merchantId=$Context.MerchantId;billId=$rev1.Registered.billId;pageSize=20} @(200)).Body
            $schedulerEffective = @($afterScheduler.items | Where-Object effectiveRun)
            Assert-AcEqual $schedulerEffective.Count 1 'scheduler convergence retains exactly one effective run'
            Assert-AcEqual $schedulerEffective[0].billRevision '2' 'scheduler convergence does not regress the authoritative revision'
            Assert-Ac -Condition (@($afterScheduler.items | Where-Object billRevision -eq '1').Count -ge $revision1Runs.Count) -Message 'scheduler convergence preserves revision-one history' -Actual $afterScheduler
            Add-AcObservation 'reconciliationRunsAfterScheduler' $afterScheduler
        }
        'PAY-AC-088' {
            $flow = New-AcExecutableSettlement $Context 100 'notify'
            $completionIdentity = Get-AcRawSha256 "MerchantSettlementCompleted:v1|$($flow.Prepared.settlementId)"
            $notificationIdentity = "merchant-notification:SETTLEMENT:$completionIdentity"
            Invoke-AcHttp POST '/api/reference-fixtures/merchant-notification-sender-script' @{notificationIdentity=$notificationIdentity;script='FAILURE'} @(200) | Out-Null
            $amount = [decimal]$flow.Detail.netMoney.amountMinor / 100
            Send-AcSettlementResult $Context $flow.Prepared.settlementId $flow.Execution $amount 'SUCCESS' 'notify' | Out-Null
            $settled = Get-SettlementDetail $flow.Prepared.settlementId
            $listed = Wait-AcNotification @{merchantId=$Context.MerchantId;sourceKind='SETTLEMENT';pageSize=10}
            Assert-AcEqual $settled.status 'SETTLED' 'settlement success is visible'
            Assert-AcEqual $listed.items.Count 1 'settlement completion notification intent is atomically visible'
            $notificationId=$listed.items[0].notificationId; $failed=(Invoke-AcHttp GET "/api/merchant-notifications/$notificationId" $null @(200)).Body.notification
            Invoke-AcHttp POST '/api/reference-fixtures/merchant-notification-sender-script' @{notificationIdentity=$failed.notificationIdentity;script='SUCCESS'} @(200) | Out-Null
            Invoke-AcHttp POST "/api/merchant-notifications/$notificationId/retries" @{idempotencyKey="$($Context.Alias)-settlement-notification-retry"} @(200) | Out-Null
            $delivered=(Invoke-AcHttp GET "/api/merchant-notifications/$notificationId" $null @(200)).Body.notification
            Assert-AcEqual $delivered.notificationIdentity $failed.notificationIdentity 'settlement notification retry preserves identity'
            Assert-AcEqual $delivered.contentIdentity $failed.contentIdentity 'settlement notification retry preserves content'
            Assert-AcEqual $delivered.status 'DELIVERED' 'settlement notification retry delivers'
            Assert-Ac -Condition ([bool](Get-SettlementDetail $flow.Prepared.settlementId).settledFactFormed) -Message 'notification retry does not alter settled fact' -Actual $settled
            Add-AcObservation 'settlement' $settled; Add-AcObservation 'merchantNotification' $delivered
        }
        default { throw "Unknown scope/notification/trace scenario $Id" }
    }
}

function Invoke-ProtocolClosurePayAcScenario {
    param([string]$Id, $Context)
    switch ($Id) {
        'PAY-AC-090' {
            $invalid = Invoke-AcHttp POST '/api/payments' @{
                merchantId=$Context.MerchantId;merchantOrderNumber="$($Context.Alias)-invalid";idempotencyKey="$($Context.Alias)-invalid";money=@{currency='CNY';amountMinor='0'};paymentMethod='CARD'
            } @(400)
            $afterInvalid = (Invoke-AcHttp POST '/api/payments/search' @{merchantId=$Context.MerchantId;pageSize=20} @(200)).Body
            $accepted = New-AcPayment $Context 100 'CNY' 'invalid' 'invalid' $Context.BaseInstant.AddHours(1)
            $replay = New-AcPayment $Context 100 'CNY' 'invalid' 'invalid' $Context.BaseInstant.AddHours(1)
            $operationBeforeConflict = (Invoke-AcHttp GET $accepted.receipt.readAfter.operationUrl $null @(200)).Body.operation
            $conflict = Invoke-AcHttp POST '/api/payments' @{
                merchantId=$Context.MerchantId;merchantOrderNumber="$($Context.Alias)-invalid";idempotencyKey="$($Context.Alias)-invalid";money=@{currency='CNY';amountMinor='12000'};paymentMethod='CARD'
            } @(400,409)
            $afterConflict = (Invoke-AcHttp POST '/api/payments/search' @{merchantId=$Context.MerchantId;pageSize=20} @(200)).Body
            $operationAfterConflict = (Invoke-AcHttp GET $accepted.receipt.readAfter.operationUrl $null @(200)).Body.operation
            $paymentAfterConflict = Get-PaymentDetail $accepted.paymentId

            $budgetPayment = New-AcSucceededPayment $Context 100 'budget'
            Set-AcClock $Context.BaseInstant.AddMinutes(3) | Out-Null
            $budgetBefore = Get-PaymentDetail $budgetPayment.PaymentId
            $refundsBefore = (Invoke-AcHttp POST '/api/refunds/search' @{merchantId=$Context.MerchantId;paymentId=$budgetPayment.PaymentId;pageSize=20} @(200)).Body
            $budgetRejected = Invoke-AcHttp POST '/api/refunds' @{
                merchantId=$Context.MerchantId;idempotencyKey="$($Context.Alias)-refund-budget";merchantRefundNo="$($Context.Alias)-refund-budget"
                paymentId=$budgetPayment.PaymentId;money=@{currency='CNY';amountMinor='10100'};reason='reference HTTP insufficient budget'
            } @(400,409)
            $budgetAfterRejected = Get-PaymentDetail $budgetPayment.PaymentId
            $refundsAfterRejected = (Invoke-AcHttp POST '/api/refunds/search' @{merchantId=$Context.MerchantId;paymentId=$budgetPayment.PaymentId;pageSize=20} @(200)).Body
            $budgetRecovery = (Invoke-AcHttp POST '/api/refunds' @{
                merchantId=$Context.MerchantId;idempotencyKey="$($Context.Alias)-refund-budget";merchantRefundNo="$($Context.Alias)-refund-budget"
                paymentId=$budgetPayment.PaymentId;money=@{currency='CNY';amountMinor='1000'};reason='reference HTTP affordable refund'
            } @(201)).Body

            Assert-AcEqual (Get-AcErrorCode $invalid.Body) 'VALIDATION_ERROR' 'synchronous validation returns only ApiError'
            Assert-AcEqual $afterInvalid.items.Count 0 'invalid Money creates no payment resource'
            Assert-AcEqual $accepted.receipt.acceptanceStatus 'ACCEPTED' 'normal command returns ACCEPTED'
            Assert-AcEqual $accepted.status 'PAYABLE' 'accepted receipt is not payment business success'
            Assert-AcEqual $replay.receipt.acceptanceStatus 'ALREADY_ACCEPTED' 'same payload replay returns ALREADY_ACCEPTED'
            Assert-AcEqual $replay.receipt.operationId $accepted.receipt.operationId 'replay returns stable operationId'
            Assert-AcEqual (Get-AcErrorCode $conflict.Body) 'IDEMPOTENCY_CONFLICT' 'same key with different payload is synchronously rejected'
            Assert-AcEqual $afterConflict.items.Count 1 'idempotency conflict creates no second payment resource'
            Assert-AcEqual $afterConflict.items[0].money.amountMinor '10000' 'idempotency conflict does not change original payment Money'
            Assert-AcEqual $operationAfterConflict.operationId $operationBeforeConflict.operationId 'idempotency conflict creates no replacement Operation'
            Assert-AcEqual $operationAfterConflict.updatedAt $operationBeforeConflict.updatedAt 'idempotency conflict does not mutate the accepted Operation'
            Assert-AcEqual (Get-AcErrorCode $budgetRejected.Body) 'REFUND_BUDGET_EXCEEDED' 'insufficient refund budget is synchronously rejected'
            Assert-AcEqual $refundsBefore.items.Count 0 'budget rejection starts with no refund resource'
            Assert-AcEqual $refundsAfterRejected.items.Count 0 'budget rejection creates no refund resource'
            foreach($field in @('originalAmount','succeededAmount','reservedAmount','availableAmount')){
                Assert-AcEqual $budgetAfterRejected.refundBudget.$field.currency $budgetBefore.refundBudget.$field.currency "budget rejection keeps $field currency unchanged"
                Assert-AcEqual $budgetAfterRejected.refundBudget.$field.amountMinor $budgetBefore.refundBudget.$field.amountMinor "budget rejection keeps $field amount unchanged"
            }
            Assert-AcEqual $budgetRecovery.receipt.acceptanceStatus 'ACCEPTED' 'rejected budget request did not create an Operation or poison its idempotency key'
            Assert-AcEqual $operationAfterConflict.status 'SUCCEEDED' 'accepted command Operation is queryable and complete'
            Assert-AcEqual $paymentAfterConflict.status 'PAYABLE' 'command success does not imply payment business success'
            Add-AcObservation 'synchronousRejections' @{invalidMoney=$invalid.Body;idempotencyConflict=$conflict.Body;refundBudget=$budgetRejected.Body}
            Add-AcObservation 'acceptedOperation' $operationAfterConflict
            Add-AcObservation 'payment' $paymentAfterConflict
            Add-AcObservation 'budgetAfterRejected' $budgetAfterRejected
        }
        'PAY-AC-091' {
            $operations = [ordered]@{}
            foreach ($outcome in @('SUCCEEDED','FAILED','REVIEW_REQUIRED')) {
                $suffix = $outcome.ToLowerInvariant().Replace('_','-')
                $resourceId = "$($Context.Alias)-resource-$suffix"
                $created = (Invoke-AcHttp POST '/api/reference-fixtures/operations' @{
                    merchantId=$Context.MerchantId;commandType='ReferenceFixtureOperation';idempotencyKey="$($Context.Alias)-poll-$suffix"
                    resourceType='REFERENCE_FIXTURE';resourceId=$resourceId;readAfterMode='POLL';resourceUrl="/api/reference-fixtures/operation-resources/$resourceId"
                } @(200)).Body
                $operationId = $created.receipt.operationId
                $notReady = Invoke-AcHttp GET $created.receipt.readAfter.resourceUrl $null @(409)
                $accepted = (Invoke-AcHttp GET $created.receipt.readAfter.operationUrl $null @(200)).Body.operation
                $processing = (Invoke-AcHttp POST "/api/reference-fixtures/operations/$operationId/transitions" @{outcome='PROCESSING'} @(200)).Body.operation

                Assert-AcEqual $created.receipt.readAfter.mode 'POLL' "$outcome fixture returns POLL readAfter"
                Assert-AcEqual (Get-AcErrorCode $notReady.Body) 'RESOURCE_NOT_READY' "$outcome resource projection returns RESOURCE_NOT_READY before terminal convergence"
                Assert-Ac -Condition ([bool]$notReady.Body.retryable) -Message "$outcome RESOURCE_NOT_READY is retryable" -Actual $notReady.Body
                Assert-AcEqual $notReady.Body.details.operationId $operationId "$outcome not-ready details identify Operation"
                Assert-AcEqual ([int]$notReady.Body.details.retryAfterMs) ([int]$created.receipt.readAfter.retryAfterMs) "$outcome not-ready details preserve the receipt retry hint"
                Assert-AcEqual $accepted.status 'ACCEPTED' "$outcome Operation is readable from acceptance"
                Assert-AcEqual $accepted.finality 'NON_FINAL' "$outcome accepted Operation is non-final"
                Assert-AcEqual $processing.status 'PROCESSING' "$outcome Operation exposes processing state"
                Assert-AcEqual $processing.finality 'NON_FINAL' "$outcome processing Operation remains non-final"

                if ($outcome -eq 'SUCCEEDED') {
                    $terminal = (Invoke-AcHttp POST "/api/reference-fixtures/operations/$operationId/transitions" @{
                        outcome='SUCCEEDED';resourceUrl=$created.receipt.readAfter.resourceUrl;result=@{fixtureId=$operationId;outcome='SUCCEEDED'}
                    } @(200)).Body.operation
                    $resource = (Invoke-AcHttp GET $created.receipt.readAfter.resourceUrl $null @(200)).Body.operation
                    Assert-AcEqual $terminal.status 'SUCCEEDED' 'successful POLL Operation converges to SUCCEEDED'
                    Assert-AcEqual $terminal.finality 'FINAL' 'successful POLL Operation is final'
                    Assert-AcEqual $terminal.result.fixtureId $operationId 'successful Operation supplies the authoritative resource result'
                    Assert-AcEqual $resource.operationId $operationId 'resourceUrl becomes readable for the successful Operation'
                    Assert-AcEqual $resource.status 'SUCCEEDED' 'ready resource exposes the successful Operation'
                } elseif ($outcome -eq 'FAILED') {
                    $terminal = (Invoke-AcHttp POST "/api/reference-fixtures/operations/$operationId/transitions" @{
                        outcome='FAILED';errorCode='REFERENCE_EXECUTION_FAILED';errorMessage='reference operation failed deterministically'
                        errorDetails=@{stage='REFERENCE_EXECUTOR';reason='DECLINED'};retryable=$false
                    } @(200)).Body.operation
                    $resourceAfter = Invoke-AcHttp GET $created.receipt.readAfter.resourceUrl $null @(409)
                    Assert-AcEqual $terminal.status 'FAILED' 'failed POLL Operation converges to FAILED'
                    Assert-AcEqual $terminal.finality 'FINAL' 'failed POLL Operation is final'
                    Assert-AcEqual $terminal.error.code 'REFERENCE_EXECUTION_FAILED' 'failed Operation exposes a stable error code'
                    Assert-AcEqual $terminal.error.message 'reference operation failed deterministically' 'failed Operation exposes the stable error message'
                    Assert-AcEqual $terminal.error.details.stage 'REFERENCE_EXECUTOR' 'failed Operation exposes stable error details'
                    Assert-AcEqual $terminal.error.details.reason 'DECLINED' 'failed Operation preserves the executor reason'
                    Assert-AcEqual ([bool]$terminal.error.retryable) $false 'failed Operation exposes retryability'
                    Assert-AcEqual (Get-AcErrorCode $resourceAfter.Body) 'RESOURCE_NOT_READY' 'failed Operation does not fabricate a ready resource'
                } else {
                    $reviewId = "MR-$($Context.Alias)-operation"
                    $terminal = (Invoke-AcHttp POST "/api/reference-fixtures/operations/$operationId/transitions" @{outcome='REVIEW_REQUIRED';reviewId=$reviewId} @(200)).Body.operation
                    $resourceAfter = Invoke-AcHttp GET $created.receipt.readAfter.resourceUrl $null @(409)
                    Assert-AcEqual $terminal.status 'REVIEW_REQUIRED' 'review POLL Operation converges to REVIEW_REQUIRED'
                    Assert-AcEqual $terminal.finality 'REVIEW_REQUIRED' 'review POLL Operation exposes review finality'
                    Assert-AcEqual $terminal.reviewId $reviewId 'review Operation exposes the stable manual-review reference'
                    Assert-AcEqual (Get-AcErrorCode $resourceAfter.Body) 'RESOURCE_NOT_READY' 'review-required Operation does not fabricate a ready resource'
                }
                $operations[$outcome] = $terminal
            }
            Add-AcObservation 'operationTerminalMatrix' $operations
        }
        'PAY-AC-092' {
            $resourceId = "$($Context.Alias)-timeout"
            $created = (Invoke-AcHttp POST '/api/reference-fixtures/operations' @{
                merchantId=$Context.MerchantId;commandType='ReferenceFixtureOperation';idempotencyKey="$($Context.Alias)-timeout";resourceType='REFERENCE_FIXTURE';resourceId=$resourceId;readAfterMode='POLL';resourceUrl="/api/reference-fixtures/operation-resources/$resourceId"
            } @(200)).Body
            $operationId=$created.receipt.operationId
            $before=(Invoke-AcHttp GET "/api/operations/$operationId" $null @(200)).Body.operation
            $policy=(Invoke-AcHttp GET '/api/reference-fixtures/policy' $null @(200)).Body.policy
            Advance-AcClock $policy.operationObservationTimeout | Out-Null
            $after=(Invoke-AcHttp GET "/api/operations/$operationId" $null @(200)).Body.operation
            Assert-AcEqual $policy.operationObservationTimeout 'PT30S' 'reference observation timeout input is PT30S'
            Assert-AcEqual $after.status $before.status 'client observation timeout does not change Operation status'
            Assert-AcEqual $after.updatedAt $before.updatedAt 'client observation timeout writes no Operation fact'
            $progress=(Invoke-AcHttp POST "/api/reference-fixtures/operations/$operationId/transitions" @{outcome='PROCESSING'} @(200)).Body.operation
            Assert-AcEqual $progress.status 'PROCESSING' 'same Operation remains usable after observation timeout'
            Add-AcObservation 'observation' @{result='OBSERVATION_TIMEOUT';operation=$after}
        }
        'PAY-AC-093' {
            $p1=New-AcPayment $Context 10 'CNY' 'page1' 'page1'; $p2=New-AcPayment $Context 20 'CNY' 'page2' 'page2'
            $paymentPage1=(Invoke-AcHttp POST '/api/payments/search' @{merchantId=$Context.MerchantId;pageSize=1} @(200)).Body
            $p3=New-AcPayment $Context 30 'CNY' 'newer' 'newer'
            $paymentPage2=(Invoke-AcHttp POST '/api/payments/search' @{merchantId=$Context.MerchantId;pageSize=1;cursor=$paymentPage1.nextCursor} @(200)).Body
            $paymentInvalid=Invoke-AcHttp POST '/api/payments/search' @{merchantId="$($Context.MerchantId)-changed";pageSize=1;cursor=$paymentPage1.nextCursor} @(400)
            $paymentForged=Invoke-AcHttp POST '/api/payments/search' @{merchantId=$Context.MerchantId;pageSize=1;cursor=(Get-AcForgedCursor $paymentPage1.nextCursor)} @(400)
            $success=New-AcSucceededPayment $Context 100 'list-refunds'
            Set-AcClock $Context.BaseInstant.AddMinutes(3) | Out-Null
            $r1=New-AcRefundRequest $Context $success.PaymentId 10 'list1';$r2=New-AcRefundRequest $Context $success.PaymentId 10 'list2'
            $refundPage1=(Invoke-AcHttp POST '/api/refunds/search' @{merchantId=$Context.MerchantId;pageSize=1} @(200)).Body
            $r3=New-AcRefundRequest $Context $success.PaymentId 10 'list3'
            $refundPage2=(Invoke-AcHttp POST '/api/refunds/search' @{merchantId=$Context.MerchantId;pageSize=1;cursor=$refundPage1.nextCursor} @(200)).Body
            $refundInvalid=Invoke-AcHttp POST '/api/refunds/search' @{merchantId="$($Context.MerchantId)-changed";pageSize=1;cursor=$refundPage1.nextCursor} @(400)
            $refundForged=Invoke-AcHttp POST '/api/refunds/search' @{merchantId=$Context.MerchantId;pageSize=1;cursor=(Get-AcForgedCursor $refundPage1.nextCursor)} @(400)
            $record=New-AcBillRecord "$($Context.Alias)-list-run" 'PAYMENT' $success.TransactionId 100 $success.OccurredAt
            $bill=Register-AcBillAndSignal $Context @($record) '1' 'list-run'
            $rerun=(Invoke-AcHttp POST "/api/reconciliation-runs/$($bill.Signal.runId)/reruns" @{idempotencyKey="$($Context.Alias)-list-run-rerun"} @(200) 'fixture-reconciliation-operator').Body
            $nextRevision=Register-AcBillAndSignal $Context @($record) '2' 'list-run'
            $runPage1=(Invoke-AcHttp POST '/api/reconciliation-runs/search' @{merchantId=$Context.MerchantId;pageSize=1} @(200)).Body
            $runPage2=(Invoke-AcHttp POST '/api/reconciliation-runs/search' @{merchantId=$Context.MerchantId;pageSize=1;cursor=$runPage1.nextCursor} @(200)).Body
            $runInvalid=Invoke-AcHttp POST '/api/reconciliation-runs/search' @{merchantId="$($Context.MerchantId)-changed";pageSize=1;cursor=$runPage1.nextCursor} @(400)
            $runForged=Invoke-AcHttp POST '/api/reconciliation-runs/search' @{merchantId=$Context.MerchantId;pageSize=1;cursor=(Get-AcForgedCursor $runPage1.nextCursor)} @(400)
            $settlement=New-AcPreparedSettlement $Context 'list';$voided=(Invoke-AcHttp POST "/api/merchant-settlements/$($settlement.settlementId)/voids" @{reason='list replacement';evidence='evidence://list';idempotencyKey="$($Context.Alias)-list-void";createReplacement=$true} @(200) 'fixture-settlement-operator').Body
            $settlementPage1=(Invoke-AcHttp POST '/api/merchant-settlements/search' @{merchantId=$Context.MerchantId;pageSize=1} @(200)).Body
            $settlementPage2=(Invoke-AcHttp POST '/api/merchant-settlements/search' @{merchantId=$Context.MerchantId;pageSize=1;cursor=$settlementPage1.nextCursor} @(200)).Body
            $settlementInvalid=Invoke-AcHttp POST '/api/merchant-settlements/search' @{merchantId="$($Context.MerchantId)-changed";pageSize=1;cursor=$settlementPage1.nextCursor} @(400)
            $settlementForged=Invoke-AcHttp POST '/api/merchant-settlements/search' @{merchantId=$Context.MerchantId;pageSize=1;cursor=(Get-AcForgedCursor $settlementPage1.nextCursor)} @(400)
            $unknown=New-AcPayment $Context 15 'CNY' 'review1' 'review1' $Context.BaseInstant.AddMinutes(5);Invoke-AcHttp POST '/api/reference-fixtures/payment-channel-script' @{channelId='C-001';script='NO_RESULT'} @(200)|Out-Null
            $ua=New-AcPaymentAttempt $Context $unknown.paymentId 'review1';Submit-AcPaymentAttempt $Context $unknown.paymentId $ua.paymentAttemptId 'review1'|Out-Null
            $unknown2=New-AcPayment $Context 16 'CNY' 'review2' 'review2' $Context.BaseInstant.AddMinutes(5);$ub=New-AcPaymentAttempt $Context $unknown2.paymentId 'review2';Submit-AcPaymentAttempt $Context $unknown2.paymentId $ub.paymentAttemptId 'review2'|Out-Null
            Set-AcClock $Context.BaseInstant.AddMinutes(10)|Out-Null;Invoke-AcHttp POST '/api/reference-fixtures/maintenance' @{action='PAYMENT_EXPIRY'} @(200)|Out-Null
            $reviewPage1=Search-ManualReviews @{merchantId=$Context.MerchantId;pageSize=1};$reviewPage2=Search-ManualReviews @{merchantId=$Context.MerchantId;pageSize=1;cursor=$reviewPage1.nextCursor}
            $reviewInvalid=Invoke-AcHttp POST '/api/manual-reviews/search' @{merchantId="$($Context.MerchantId)-changed";pageSize=1;cursor=$reviewPage1.nextCursor} @(400)
            $reviewForged=Invoke-AcHttp POST '/api/manual-reviews/search' @{merchantId=$Context.MerchantId;pageSize=1;cursor=(Get-AcForgedCursor $reviewPage1.nextCursor)} @(400)
            foreach($pair in @(@('payment',$paymentPage1,$paymentPage2),@('refund',$refundPage1,$refundPage2),@('run',$runPage1,$runPage2),@('settlement',$settlementPage1,$settlementPage2),@('review',$reviewPage1,$reviewPage2))){
                Assert-Ac -Condition ($pair[1].items.Count -eq 1 -and $pair[2].items.Count -eq 1) -Message "$($pair[0]) list supports small-page cursor traversal" -Actual @{first=$pair[1];second=$pair[2]}
                $id1=($pair[1].items[0].PSObject.Properties|Where-Object Name -match '^(paymentId|refundId|runId|settlementId|reviewId)$'|Select-Object -First 1).Value
                $id2=($pair[2].items[0].PSObject.Properties|Where-Object Name -match '^(paymentId|refundId|runId|settlementId|reviewId)$'|Select-Object -First 1).Value
                Assert-Ac -Condition ($id1 -ne $id2) -Message "$($pair[0]) cursor pages do not duplicate items" -Actual @($id1,$id2)
            }
            foreach($invalid in @($paymentInvalid,$refundInvalid,$runInvalid,$settlementInvalid,$reviewInvalid)){
                Assert-AcEqual (Get-AcErrorCode $invalid.Body) 'INVALID_CURSOR' 'cursor is bound to the complete list filter'
                Assert-AcEqual $invalid.Body.details.field 'cursor' 'invalid cursor details identify the rejected field'
                Assert-AcEqual $invalid.Body.details.reason 'FILTER_MISMATCH_OR_INVALID' 'invalid cursor details identify filter binding or malformed input'
            }
            foreach($forged in @($paymentForged,$refundForged,$runForged,$settlementForged,$reviewForged)){
                Assert-AcEqual (Get-AcErrorCode $forged.Body) 'INVALID_CURSOR' 'forged cursor is rejected even when the filter is unchanged'
                Assert-AcEqual $forged.Body.details.field 'cursor' 'forged cursor details identify the rejected field'
            }
            Assert-Ac -Condition (@($paymentPage2.items.paymentId) -notcontains $p3.paymentId) -Message 'newer payment does not backfill an existing cursor' -Actual $paymentPage2
            Add-AcObservation 'fiveAuthorityLists' @{payments=@($paymentPage1,$paymentPage2);refunds=@($refundPage1,$refundPage2);runs=@($runPage1,$runPage2);settlements=@($settlementPage1,$settlementPage2);reviews=@($reviewPage1,$reviewPage2)}
        }
        'PAY-AC-094' {
            $payment=New-AcSucceededPayment $Context 100 'authority';$record=New-AcBillRecord "$($Context.Alias)-authority" 'PAYMENT' $payment.TransactionId 100 $payment.OccurredAt
            $rev1=Register-AcBillAndSignal $Context @($record) '1' 'authority'
            $registered2=(Invoke-AcHttp POST '/api/reference-fixtures/bills' @{channelId='C-001';billIdentity=$rev1.BillIdentity;businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai';revision='2';completeness='COMPLETE';rawEvidence='evidence://rev2';payloadFingerprint="$($Context.Alias)-rev2";publishedAt=$Context.BaseInstant.AddHours(13).ToString('o');records=@($record)} @(200)).Body
            $signal2=(Invoke-AcHttp POST "/api/reference-fixtures/bills/$($rev1.BillIdentity)/signals" @{channelId='C-001';businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai';signalIdentity="$($Context.Alias)-rev2-signal";announcedRevision='2';publishedAt=$Context.BaseInstant.AddHours(13).ToString('o')} @(200)).Body
            $rerun=(Invoke-AcHttp POST "/api/reconciliation-runs/$($signal2.runId)/reruns" @{idempotencyKey="$($Context.Alias)-authority-rerun"} @(200) 'fixture-reconciliation-operator').Body
            $runs=(Invoke-AcHttp POST '/api/reconciliation-runs/search' @{merchantId=$Context.MerchantId;billId=$registered2.billId;pageSize=20} @(200)).Body
            $batchRoute=Invoke-AcHttp GET "/api/reconciliation-batches/$($registered2.billId)" $null @(404)
            Assert-AcEqual $registered2.currentRevision '2' 'bill revision pointer advances monotonically'
            Assert-Ac -Condition ($runs.items.Count -ge 2) -Message 'old and new ReconciliationRun history is preserved' -Actual $runs
            Assert-Ac -Condition (@($runs.items|Where-Object effectiveRun).Count -eq 1) -Message 'only one ReconciliationRun is effective' -Actual $runs
            Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace([string]$rerun.runId)) -Message 'rerun command returns ReconciliationRun identity' -Actual $rerun
            Assert-AcEqual $batchRoute.StatusCode 404 'no parallel public reconciliation batch resource exists'
            Add-AcObservation 'reconciliationRuns' $runs
        }
        'PAY-AC-095' {
            $payment = New-AcSucceededPayment $Context 100 'append'
            $record = New-AcBillRecord "$($Context.Alias)-append" 'PAYMENT' $payment.TransactionId 99 $payment.OccurredAt
            $bill = Register-AcBillAndSignal $Context @($record) '1' 'append'
            $item = $bill.Run.differences[0]
            $path = "/api/reconciliation-runs/$($bill.Signal.runId)/differences/$($item.itemId)/dispositions"
            $body = @{merchantId=$Context.MerchantId;channelId='C-001';conclusion='NO_SETTLEMENT_IMPACT';settlementImpact='DOES_NOT_BLOCK_SETTLEMENT';reason='reviewed evidence';evidence='evidence://append';idempotencyKey="$($Context.Alias)-append"}
            $missing = Invoke-AcHttp POST $path $body @(400)
            $unknownActor = Invoke-AcHttp POST $path $body @(400) 'unknown-reference-actor'
            $before = (Invoke-AcHttp GET "/api/reconciliation-runs/$($bill.Signal.runId)" $null @(200)).Body
            $beforeHistory = @($before.differences | Where-Object itemId -eq $item.itemId)[0]
            $first = (Invoke-AcHttp POST $path $body @(200) 'fixture-reconciliation-operator').Body
            $replay = (Invoke-AcHttp POST $path $body @(200) 'fixture-reconciliation-operator').Body
            $after = (Invoke-AcHttp GET "/api/reconciliation-runs/$($bill.Signal.runId)" $null @(200)).Body
            $history = @($after.differences | Where-Object itemId -eq $item.itemId)[0]
            Assert-AcEqual (Get-AcErrorCode $missing.Body) 'VALIDATION_ERROR' 'missing actor rejects before side effects'
            Assert-AcEqual (Get-AcErrorCode $unknownActor.Body) 'VALIDATION_ERROR' 'unknown actor rejects before side effects'
            Assert-Ac -Condition (($missing.Body.PSObject.Properties.Name -notcontains 'receipt') -and ($unknownActor.Body.PSObject.Properties.Name -notcontains 'receipt')) -Message 'invalid actor contexts create no disposition Operation receipt' -Actual @($missing.Body,$unknownActor.Body)
            Assert-AcEqual $beforeHistory.dispositions.Count 0 'invalid actor contexts append no disposition'
            Assert-AcEqual $first.actorId 'reference-reconciliation-operator' 'disposition records trusted actor'
            Assert-AcEqual $first.receipt.acceptanceStatus 'ACCEPTED' 'valid disposition is accepted after invalid attempts with the same key'
            Assert-AcEqual $replay.receipt.operationId $first.receipt.operationId 'same disposition replays one Operation'
            Assert-AcEqual $history.dispositions.Count 1 'replay does not append another disposition'
            Assert-AcEqual $history.differenceType 'AMOUNT_MISMATCH' 'original difference remains unchanged'
            Assert-Ac -Condition (-not [bool]$history.settlementBlocked) -Message 'explicit non-blocking conclusion releases blocker' -Actual $history

            $confirmationContext = New-AcChildContext $Context 'confirmation' 1
            Set-AcClock $confirmationContext.BaseInstant | Out-Null
            $created = New-AcPayment $confirmationContext 100 'CNY' 'confirmation' 'confirmation'
            $attempt = New-AcPaymentAttempt $confirmationContext $created.paymentId 'confirmation'
            Submit-AcPaymentAttempt $confirmationContext $created.paymentId $attempt.paymentAttemptId 'confirmation' | Out-Null
            $unknownResult = Send-AcPaymentResult $confirmationContext $created.paymentId $attempt.paymentAttemptId 100 'UNKNOWN' 'confirmation' $true
            $confirmationRecord = New-AcBillRecord "$($confirmationContext.Alias)-status" 'PAYMENT' $unknownResult.TransactionId 100 $unknownResult.OccurredAt 'SUCCEEDED'
            $confirmationBill = Register-AcBillAndSignal $confirmationContext @($confirmationRecord) '1' 'confirmation'
            $confirmationItem = @($confirmationBill.Run.differences | Where-Object differenceType -eq 'STATUS_MISMATCH')[0]
            $confirmationPath = "/api/reconciliation-runs/$($confirmationBill.Signal.runId)/differences/$($confirmationItem.itemId)/confirmations"
            $confirmationBody = @{merchantId=$confirmationContext.MerchantId;channelId='C-001';reason='confirmed append-only platform fact';evidence='evidence://confirmation-append';idempotencyKey="$($confirmationContext.Alias)-confirm"}
            $confirmationMissing = Invoke-AcHttp POST $confirmationPath $confirmationBody @(400)
            $confirmationUnknownActor = Invoke-AcHttp POST $confirmationPath $confirmationBody @(400) 'unknown-reference-actor'
            $confirmationBefore = (Invoke-AcHttp GET "/api/reconciliation-runs/$($confirmationBill.Signal.runId)" $null @(200)).Body
            $confirmationBeforeItem = @($confirmationBefore.differences | Where-Object itemId -eq $confirmationItem.itemId)[0]
            $confirmationFirst = (Invoke-AcHttp POST $confirmationPath $confirmationBody @(200) 'fixture-reconciliation-operator').Body
            $confirmationReplay = (Invoke-AcHttp POST $confirmationPath $confirmationBody @(200) 'fixture-reconciliation-operator').Body
            $confirmationAfter = (Invoke-AcHttp GET "/api/reconciliation-runs/$($confirmationBill.Signal.runId)" $null @(200)).Body
            $confirmationHistory = @($confirmationAfter.differences | Where-Object itemId -eq $confirmationItem.itemId)[0]
            Assert-AcEqual (Get-AcErrorCode $confirmationMissing.Body) 'VALIDATION_ERROR' 'missing actor rejects fact confirmation before side effects'
            Assert-AcEqual (Get-AcErrorCode $confirmationUnknownActor.Body) 'VALIDATION_ERROR' 'unknown actor rejects fact confirmation before side effects'
            Assert-Ac -Condition (($confirmationMissing.Body.PSObject.Properties.Name -notcontains 'receipt') -and ($confirmationUnknownActor.Body.PSObject.Properties.Name -notcontains 'receipt')) -Message 'invalid actor contexts create no confirmation Operation receipt' -Actual @($confirmationMissing.Body,$confirmationUnknownActor.Body)
            Assert-AcEqual $confirmationBeforeItem.confirmationFacts.Count 0 'invalid actor contexts append no confirmation fact'
            Assert-AcEqual $confirmationFirst.actorId 'reference-reconciliation-operator' 'confirmation records trusted actor'
            Assert-AcEqual $confirmationFirst.receipt.acceptanceStatus 'ACCEPTED' 'valid confirmation is accepted after invalid attempts with the same key'
            Assert-AcEqual $confirmationReplay.receipt.operationId $confirmationFirst.receipt.operationId 'same confirmation replays one Operation'
            Assert-AcEqual $confirmationHistory.confirmationFacts.Count 1 'confirmation replay appends one fact only'
            Assert-AcEqual $confirmationHistory.platformRawStatus 'RESULT_UNKNOWN' 'confirmation preserves original platform fact evidence'
            Assert-AcEqual $confirmationHistory.channelRawStatus 'SUCCEEDED' 'confirmation preserves original bill evidence'
            Add-AcObservation 'differenceHistory' $history
            Add-AcObservation 'confirmationHistory' $confirmationHistory
        }
        'PAY-AC-096' {
            $consumedContext = [pscustomobject]@{
                ScenarioId=$Context.ScenarioId;Alias="$($Context.Alias)-consumed";MerchantId=$Context.MerchantId;ChannelId=$Context.ChannelId
                BaseInstant=$Context.BaseInstant;BusinessDate=$Context.BusinessDate;ChannelConfiguration=$Context.ChannelConfiguration
            }
            Set-AcClock $consumedContext.BaseInstant | Out-Null
            $consumed = New-AcSucceededPayment $consumedContext 30 'consumed'
            $consumedRecord = New-AcBillRecord "$($Context.Alias)-consumed-r1" 'PAYMENT' $consumed.TransactionId 30 $consumed.OccurredAt
            $bill = Register-AcBillAndSignal $Context @($consumedRecord) '1' 'candidates'
            $narrowPeriod = @{start=$Context.BaseInstant.ToString('o');end=$Context.BaseInstant.AddMinutes(10).ToString('o');timezone='Asia/Shanghai'}
            $consumingSettlement = New-AcPreparedSettlement $Context 'consumed' $narrowPeriod
            Confirm-AcSettlement $Context $consumingSettlement.settlementId 'consumed' | Out-Null
            $consumedDetail = Get-SettlementDetail $consumingSettlement.settlementId

            $activeContext = [pscustomobject]@{
                ScenarioId=$Context.ScenarioId;Alias="$($Context.Alias)-active";MerchantId=$Context.MerchantId;ChannelId=$Context.ChannelId
                BaseInstant=$Context.BaseInstant.AddMinutes(20);BusinessDate=$Context.BusinessDate;ChannelConfiguration=$Context.ChannelConfiguration
            }
            Set-AcClock $activeContext.BaseInstant | Out-Null
            $eligible = New-AcSucceededPayment $activeContext 100 'eligible'
            $refund = New-AcSucceededRefund $activeContext $eligible.PaymentId 20 'successful-refund'
            $unresolved = New-AcSucceededPayment $activeContext 50 'unresolved'
            $adjustmentSource = New-AcPayment $activeContext 40 'CNY' 'confirmed-adjustment' 'confirmed-adjustment'
            $adjustmentAttempt = New-AcPaymentAttempt $activeContext $adjustmentSource.paymentId 'confirmed-adjustment'
            Submit-AcPaymentAttempt $activeContext $adjustmentSource.paymentId $adjustmentAttempt.paymentAttemptId 'confirmed-adjustment' | Out-Null
            $adjustmentUnknown = Send-AcPaymentResult $activeContext $adjustmentSource.paymentId $adjustmentAttempt.paymentAttemptId 40 'UNKNOWN' 'confirmed-adjustment' $true
            $revision2Records = @(
                $consumedRecord,
                (New-AcBillRecord "$($Context.Alias)-eligible-r2" 'PAYMENT' $eligible.TransactionId 100 $eligible.OccurredAt),
                (New-AcBillRecord "$($Context.Alias)-refund-r2" 'REFUND' $refund.ChannelRefundId 20 $refund.OccurredAt),
                (New-AcBillRecord "$($Context.Alias)-unresolved-r2" 'PAYMENT' $unresolved.TransactionId 49 $unresolved.OccurredAt),
                (New-AcBillRecord "$($Context.Alias)-adjustment-r2" 'PAYMENT' $adjustmentUnknown.TransactionId 40 $adjustmentUnknown.OccurredAt 'SUCCEEDED')
            )
            Invoke-AcHttp POST '/api/reference-fixtures/bills' @{
                channelId='C-001';billIdentity=$bill.BillIdentity;businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai'
                revision='2';completeness='COMPLETE';rawEvidence="evidence://$($bill.BillIdentity)/2";payloadFingerprint="$($bill.BillIdentity)-candidates-r2"
                publishedAt=$Context.BaseInstant.AddHours(13).ToString('o');records=$revision2Records
            } @(200) | Out-Null
            $revision2Signal = (Invoke-AcHttp POST "/api/reference-fixtures/bills/$($bill.BillIdentity)/signals" @{
                channelId='C-001';businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai'
                signalIdentity="SIGNAL-$($bill.BillIdentity)-2";announcedRevision='2';publishedAt=$Context.BaseInstant.AddHours(13).ToString('o')
            } @(200)).Body
            $revision2Run = (Invoke-AcHttp GET "/api/reconciliation-runs/$($revision2Signal.runId)" $null @(200)).Body
            $adjustmentDifference = @($revision2Run.differences | Where-Object paymentId -eq $adjustmentSource.PaymentId)[0]
            $adjustmentConfirmation = (Invoke-AcHttp POST "/api/reconciliation-runs/$($revision2Signal.runId)/differences/$($adjustmentDifference.itemId)/confirmations" @{
                merchantId=$Context.MerchantId;channelId='C-001';reason='confirmed settlement adjustment'
                evidence="evidence://$($Context.Alias)/confirmed-adjustment";idempotencyKey="$($Context.Alias)-confirmed-adjustment"
            } @(200) 'fixture-reconciliation-operator').Body

            $unknownContext = [pscustomobject]@{
                ScenarioId=$Context.ScenarioId;Alias="$($Context.Alias)-unknown";MerchantId=$Context.MerchantId;ChannelId=$Context.ChannelId
                BaseInstant=$Context.BaseInstant.AddMinutes(40);BusinessDate=$Context.BusinessDate;ChannelConfiguration=$Context.ChannelConfiguration
            }
            Set-AcClock $unknownContext.BaseInstant | Out-Null
            $unknown = New-AcPayment $unknownContext 15 'CNY' 'unknown' 'unknown'
            $unknownAttempt = New-AcPaymentAttempt $unknownContext $unknown.paymentId 'unknown'
            Submit-AcPaymentAttempt $unknownContext $unknown.paymentId $unknownAttempt.paymentAttemptId 'unknown' | Out-Null
            $unknownResult = Send-AcPaymentResult $unknownContext $unknown.paymentId $unknownAttempt.paymentAttemptId 15 'UNKNOWN' 'unknown' $true

            $prepared = New-AcPreparedSettlement $Context 'candidates'
            $before = Get-SettlementDetail $prepared.settlementId
            $included = @($before.lines | Where-Object decision -eq 'INCLUDED')
            $excluded = @($before.lines | Where-Object decision -eq 'EXCLUDED')
            $includedPayment = @($included | Where-Object { $_.sourceKind -eq 'PAYMENT' -and $_.paymentId -eq $eligible.PaymentId })
            $includedRefund = @($included | Where-Object { $_.sourceKind -eq 'REFUND' -and $_.refundId -eq $refund.RefundId })
            $alreadySettled = @($excluded | Where-Object { $_.paymentId -eq $consumed.PaymentId -and $_.reasonCode -eq 'ALREADY_SETTLED' })
            $unresolvedLines = @($excluded | Where-Object { $_.paymentId -eq $unresolved.PaymentId -and $_.reasonCode -eq 'UNRESOLVED_RECONCILIATION' })
            $unknownLines = @($excluded | Where-Object { $_.paymentId -eq $unknown.paymentId -and $_.reasonCode -eq 'PAYMENT_RESULT_UNKNOWN' })
            $adjustments = @($included | Where-Object sourceKind -eq 'ADJUSTMENT')
            $includedNetMinor = [int64]0
            foreach ($line in $included) { $includedNetMinor += [int64]$line.signedNetMoney.amountMinor }

            Assert-Ac -Condition ([bool]$consumedDetail.compositionFrozen) -Message 'first narrow settlement freezes and consumes the already-settled source fact' -Actual $consumedDetail
            Assert-AcEqual $revision2Run.differenceCount 2 'current reconciliation run records the unresolved and confirmable amount mismatches'
            Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace([string]$adjustmentConfirmation.confirmationFactId)) -Message 'trusted fact confirmation creates an independent adjustment identity' -Actual $adjustmentConfirmation
            Assert-AcEqual $includedPayment.Count 1 'settleable payment has one INCLUDED candidate'
            Assert-AcEqual $includedRefund.Count 1 'successful refund has one INCLUDED candidate'
            Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace([string]$includedPayment[0].feeFactIdentity)) -Message 'included payment line references the frozen fee fact' -Actual $includedPayment[0]
            Assert-Ac -Condition ([int64]$includedPayment[0].feeMoney.amountMinor -gt 0) -Message 'included payment line exposes its fee amount' -Actual $includedPayment[0]
            Assert-AcEqual $alreadySettled.Count 1 'already-consumed payment remains queryable as EXCLUDED/ALREADY_SETTLED'
            Assert-AcEqual $unresolvedLines.Count 1 'unresolved difference remains queryable as EXCLUDED/UNRESOLVED_RECONCILIATION'
            Assert-AcEqual $unknownLines.Count 1 'unknown payment remains queryable as EXCLUDED/PAYMENT_RESULT_UNKNOWN'
            Assert-Ac -Condition (@($before.lines | Where-Object { [string]::IsNullOrWhiteSpace([string]$_.sourceFactIdentity) }).Count -eq 0) -Message 'every settlement candidate exposes a source reference' -Actual $before.lines
            Assert-Ac -Condition (@($before.lines | Where-Object { $_.decision -notin @('INCLUDED','EXCLUDED') -or [string]::IsNullOrWhiteSpace([string]$_.reasonCode) }).Count -eq 0) -Message 'every settlement candidate exposes INCLUDED/EXCLUDED and a stable reasonCode' -Actual $before.lines
            Assert-AcEqual ([string]$includedNetMinor) $before.netMoney.amountMinor 'settlement net amount is computed only from INCLUDED candidates'

            Confirm-AcSettlement $Context $prepared.settlementId 'candidates' | Out-Null
            $afterConfirmation = Get-SettlementDetail $prepared.settlementId
            $frozenLineIdentities = @($afterConfirmation.lines.lineIdentity | Sort-Object)
            $frozenNetMinor = $afterConfirmation.netMoney.amountMinor
            $frozenGrossMinor = $afterConfirmation.grossMoney.amountMinor
            $frozenRefundMinor = $afterConfirmation.refundMoney.amountMinor
            $frozenFeeMinor = $afterConfirmation.feeMoney.amountMinor
            $frozenAdjustmentMinor = $afterConfirmation.adjustmentMoney.amountMinor

            $lateContext = [pscustomobject]@{
                ScenarioId=$Context.ScenarioId;Alias="$($Context.Alias)-late";MerchantId=$Context.MerchantId;ChannelId=$Context.ChannelId
                BaseInstant=$Context.BaseInstant.AddMinutes(50);BusinessDate=$Context.BusinessDate;ChannelConfiguration=$Context.ChannelConfiguration
            }
            Set-AcClock $lateContext.BaseInstant | Out-Null
            $latePayment = New-AcSucceededPayment $lateContext 25 'late'
            $revision3Records = @($revision2Records) + @(
                (New-AcBillRecord "$($Context.Alias)-unknown-r3" 'PAYMENT' $unknownResult.TransactionId 15 $unknownResult.OccurredAt 'RESULT_UNKNOWN'),
                (New-AcBillRecord "$($Context.Alias)-late-r3" 'PAYMENT' $latePayment.TransactionId 25 $latePayment.OccurredAt)
            )
            Invoke-AcHttp POST '/api/reference-fixtures/bills' @{
                channelId='C-001';billIdentity=$bill.BillIdentity;businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai'
                revision='3';completeness='COMPLETE';rawEvidence="evidence://$($bill.BillIdentity)/3";payloadFingerprint="$($bill.BillIdentity)-candidates-r3"
                publishedAt=$Context.BaseInstant.AddHours(14).ToString('o');records=$revision3Records
            } @(200) | Out-Null
            Invoke-AcHttp POST "/api/reference-fixtures/bills/$($bill.BillIdentity)/signals" @{
                channelId='C-001';businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai'
                signalIdentity="SIGNAL-$($bill.BillIdentity)-3";announcedRevision='3';publishedAt=$Context.BaseInstant.AddHours(14).ToString('o')
            } @(200) | Out-Null
            $afterLateFact = Get-SettlementDetail $prepared.settlementId

            Assert-Ac -Condition ([bool]$afterConfirmation.compositionFrozen) -Message 'confirmation freezes the settlement composition' -Actual $afterConfirmation
            Assert-AcEqual $afterConfirmation.confirmedBy 'reference-settlement-operator' 'confirmation exposes the trusted actor'
            Assert-AcEqual (@($afterLateFact.lines.lineIdentity | Sort-Object) -join ',') ($frozenLineIdentities -join ',') 'later facts and bill revision do not rewrite frozen settlement lines'
            Assert-AcEqual $afterLateFact.netMoney.amountMinor $frozenNetMinor 'later facts do not rewrite frozen net amount'
            Assert-AcEqual $afterLateFact.grossMoney.amountMinor $frozenGrossMinor 'later facts do not rewrite frozen gross amount'
            Assert-AcEqual $afterLateFact.refundMoney.amountMinor $frozenRefundMinor 'later facts do not rewrite frozen refund amount'
            Assert-AcEqual $afterLateFact.feeMoney.amountMinor $frozenFeeMinor 'later facts do not rewrite frozen fee amount'
            Assert-AcEqual $afterLateFact.adjustmentMoney.amountMinor $frozenAdjustmentMinor 'later facts do not rewrite frozen adjustment amount'
            Add-AcObservation 'settlementCandidateMatrix' @{before=$before;afterConfirmation=$afterConfirmation;afterLateFact=$afterLateFact}

            Assert-Ac -Condition ($adjustments.Count -ge 1 -and @($adjustments | Where-Object { $_.decision -eq 'INCLUDED' -and -not [string]::IsNullOrWhiteSpace([string]$_.adjustmentSourceIdentity) }).Count -ge 1) -Message 'confirmed adjustment is queryable as an INCLUDED ADJUSTMENT candidate with source evidence' -Actual $before.lines
        }
        'PAY-AC-097' {
            $successContext=New-AcChildContext $Context 'success' 0;$success=New-AcExecutableSettlement $successContext 100 'success';$successAmount=[decimal]$success.Detail.netMoney.amountMinor/100
            Send-AcSettlementResult $successContext $success.Prepared.settlementId $success.Execution $successAmount 'SUCCESS' 'success'|Out-Null
            $failureContext=New-AcChildContext $Context 'failure' 1;Set-AcClock $failureContext.BaseInstant|Out-Null;$failure=New-AcExecutableSettlement $failureContext 100 'failure';$failureAmount=[decimal]$failure.Detail.netMoney.amountMinor/100
            Send-AcSettlementResult $failureContext $failure.Prepared.settlementId $failure.Execution $failureAmount 'FAILED' 'failure'|Out-Null
            $retry=Start-AcSettlementExecution $failureContext $failure.Prepared.settlementId 'retry'
            $unknownContext=New-AcChildContext $Context 'unknown' 2;Set-AcClock $unknownContext.BaseInstant|Out-Null;$unknown=New-AcExecutableSettlement $unknownContext 100 'unknown';$unknownAmount=[decimal]$unknown.Detail.netMoney.amountMinor/100
            Send-AcSettlementResult $unknownContext $unknown.Prepared.settlementId $unknown.Execution $unknownAmount 'UNKNOWN' 'unknown'|Out-Null
            $forbidden=Invoke-AcHttp POST "/api/merchant-settlements/$($unknown.Prepared.settlementId)/executions" @{executionChannelId='C-001';idempotencyKey="$($Context.Alias)-unknown-retry"} @(400,409)
            $successDetail=Get-SettlementDetail $success.Prepared.settlementId;$failureDetail=Get-SettlementDetail $failure.Prepared.settlementId;$unknownDetail=Get-SettlementDetail $unknown.Prepared.settlementId
            Assert-Ac -Condition ([bool]$successDetail.settledFactFormed -and $successDetail.attempts.Count -eq 1) -Message 'SUCCESS forms one settlement fact under one identity' -Actual $successDetail
            Assert-Ac -Condition ($failureDetail.attempts.Count -eq 2 -and $retry.attemptId -ne $failure.Execution.attemptId) -Message 'explicit FAILURE permits controlled new attempt identity' -Actual $failureDetail
            Assert-Ac -Condition ($unknownDetail.attempts.Count -eq 1) -Message 'UNKNOWN retains original attempt identity' -Actual $unknownDetail
            Assert-AcEqual (Get-AcErrorCode $forbidden.Body) 'RESULT_UNKNOWN_REEXECUTION_FORBIDDEN' 'UNKNOWN forbids duplicate settlement execution'
            Add-AcObservation 'successSettlement' $successDetail;Add-AcObservation 'failureSettlement' $failureDetail;Add-AcObservation 'unknownSettlement' $unknownDetail
        }
        'PAY-AC-098' {
            $voidContext=New-AcChildContext $Context 'voidable' 0;$voidable=New-AcReconciledPayment $voidContext 100 'voidable';$prepared=New-AcPreparedSettlement $voidContext 'voidable'
            $voided=(Invoke-AcHttp POST "/api/merchant-settlements/$($prepared.settlementId)/voids" @{reason='replace';evidence='evidence://replace';idempotencyKey="$($Context.Alias)-voidable";createReplacement=$true} @(200) 'fixture-settlement-operator').Body
            $original=Get-SettlementDetail $prepared.settlementId;$replacement=Get-SettlementDetail $voided.replacementSettlementId
            $unknownContext=New-AcChildContext $Context 'unknown' 1;Set-AcClock $unknownContext.BaseInstant|Out-Null;$unknown=New-AcExecutableSettlement $unknownContext 100 'unknown';Send-AcSettlementResult $unknownContext $unknown.Prepared.settlementId $unknown.Execution ([decimal]$unknown.Detail.netMoney.amountMinor/100) 'UNKNOWN' 'unknown'|Out-Null
            $forbidden=Invoke-AcHttp POST "/api/merchant-settlements/$($unknown.Prepared.settlementId)/voids" @{reason='try bypass';evidence='evidence://bypass';idempotencyKey="$($Context.Alias)-unknown-void";createReplacement=$true} @(400,409) 'fixture-settlement-operator'
            $unknownAfter=Get-SettlementDetail $unknown.Prepared.settlementId
            Assert-AcEqual $original.replacementSettlementId $replacement.settlementId 'voidable settlement links to replacement'
            Assert-AcEqual $replacement.predecessorSettlementId $original.settlementId 'replacement links back to voided settlement'
            Assert-AcEqual (Get-AcErrorCode $forbidden.Body) 'RESULT_UNKNOWN_REEXECUTION_FORBIDDEN' 'unknown execution cannot be voided/replaced'
            Assert-AcEqual $unknownAfter.attempts.Count 1 'unknown settlement retains original execution'
            Add-AcObservation 'replacementRelation' @{original=$original;replacement=$replacement};Add-AcObservation 'unknownSettlement' $unknownAfter
        }
        'PAY-AC-099' {
            $payment = New-AcSucceededPayment $Context 100 'budget'
            Set-AcClock $Context.BaseInstant.AddMinutes(3) | Out-Null

            # Success branch: a verified retryable failure closes only the current attempt.  The
            # Refund and its reservation remain live so a distinct attempt identity can continue.
            $refund = New-AcRefundRequest $Context $payment.PaymentId 40 'retry-success'
            $attempt1 = New-AcRefundAttempt $Context $refund.refundId 'retry-success-1'
            Submit-AcRefundAttempt $Context $refund.refundId $attempt1.refundAttemptId 'retry-success-1' | Out-Null
            $beforeRetry = Get-RefundDetail $refund.refundId
            $channelRefundId1 = $beforeRetry.attempts[0].channelRefundId
            $retryable = Send-AcRefundResult $Context $refund.refundId $attempt1.refundAttemptId $channelRefundId1 40 'RETRYABLE_FAILURE' 'retryable' $true
            $retryableReplay = Invoke-AcHttp POST '/api/channel/refund-results' @{
                channelId='C-001';notificationId=$retryable.NotificationId;refundId=$refund.refundId;refundAttemptId=$attempt1.refundAttemptId
                channelRefundId=$channelRefundId1;money=@{currency='CNY';amountMinor='4000'};result='RETRYABLE_FAILURE'
                occurredAt=$Context.BaseInstant.AddMinutes(5).ToString('o');rawPayload="reference-refund-$($Context.Alias)-retryable-RETRYABLE_FAILURE"
            } @(200)
            $afterRetry = Get-RefundDetail $refund.refundId
            $budgetAfterRetry = Get-PaymentDetail $payment.PaymentId
            Assert-AcEqual $retryable.Body.disposition 'ACCEPTED' 'verified retryable failure is accepted as a real channel result'
            Assert-AcEqual $retryable.Body.attemptStatus 'FAILED' 'retryable failure closes the current attempt as FAILED'
            Assert-AcEqual $retryable.Body.refundStatus 'PROCESSING' 'retryable failure keeps the Refund PROCESSING'
            Assert-AcEqual $afterRetry.attempts[0].finalResult 'RETRYABLE_FAILURE' 'attempt retains the retryable channel verdict'
            Assert-AcEqual $retryableReplay.Body.disposition 'DUPLICATE' 'retryable result replay has no second business effect'
            Assert-Ac -Condition (-not [bool]$retryable.Body.reservationReleasedNow -and -not [bool]$retryable.Body.reservationConvertedToSuccessNow) -Message 'retryable failure neither releases nor converts the reservation' -Actual $retryable.Body
            Assert-AcEqual $budgetAfterRetry.refundBudget.reservedAmount.amountMinor '4000' 'retryable failure keeps the refund budget occupied'

            $attempt2 = New-AcRefundAttempt $Context $refund.refundId 'retry-success-2'
            Assert-Ac -Condition ($attempt2.refundAttemptId -ne $attempt1.refundAttemptId) -Message 'retryable failure permits a distinct next attempt identity' -Actual @($attempt1,$attempt2)
            Submit-AcRefundAttempt $Context $refund.refundId $attempt2.refundAttemptId 'retry-success-2' | Out-Null
            $beforeUnknown = Get-RefundDetail $refund.refundId
            $channelRefundId2 = @($beforeUnknown.attempts | Where-Object refundAttemptId -eq $attempt2.refundAttemptId)[0].channelRefundId
            $unknown = Send-AcRefundResult $Context $refund.refundId $attempt2.refundAttemptId $channelRefundId2 40 'UNKNOWN' 'unknown' $true
            $unknownReplay = Invoke-AcHttp POST '/api/channel/refund-results' @{
                channelId='C-001';notificationId=$unknown.NotificationId;refundId=$refund.refundId;refundAttemptId=$attempt2.refundAttemptId
                channelRefundId=$channelRefundId2;money=@{currency='CNY';amountMinor='4000'};result='UNKNOWN'
                occurredAt=$Context.BaseInstant.AddMinutes(5).ToString('o');rawPayload="reference-refund-$($Context.Alias)-unknown-UNKNOWN"
            } @(200)
            Set-AcClock $Context.BaseInstant.AddMinutes(20) | Out-Null
            $reviewMaintenance = (Invoke-AcHttp POST '/api/reference-fixtures/maintenance' @{action='REFUND_UNKNOWN_REVIEW'} @(200)).Body
            $underReview = Get-RefundDetail $refund.refundId
            $budgetUnderReview = Get-PaymentDetail $payment.PaymentId
            Assert-AcEqual $unknownReplay.Body.disposition 'DUPLICATE' 'unknown result replay has no second business effect'
            Assert-AcEqual $underReview.status 'RESULT_UNKNOWN' 'unknown result remains explicit before threshold processing'
            Assert-Ac -Condition ($reviewMaintenance.changedCount -ge 1) -Message 'overdue unknown result is selected by review maintenance' -Actual $reviewMaintenance
            Assert-AcEqual $underReview.finality 'REVIEW_REQUIRED' 'overdue unknown attempt becomes review-required'
            Assert-AcEqual $budgetUnderReview.refundBudget.reservedAmount.amountMinor '4000' 'unknown review continues to occupy refund budget'

            $success = Send-AcRefundResult $Context $refund.refundId $attempt2.refundAttemptId $channelRefundId2 40 'SUCCESS' 'success' $true
            $successReplay = Invoke-AcHttp POST '/api/channel/refund-results' @{
                channelId='C-001';notificationId=$success.NotificationId;refundId=$refund.refundId;refundAttemptId=$attempt2.refundAttemptId
                channelRefundId=$channelRefundId2;money=@{currency='CNY';amountMinor='4000'};result='SUCCESS'
                occurredAt=$Context.BaseInstant.AddMinutes(5).ToString('o');rawPayload="reference-refund-$($Context.Alias)-success-SUCCESS"
            } @(200)
            $late = Send-AcRefundResult $Context $refund.refundId $attempt2.refundAttemptId $channelRefundId2 40 'FAILED' 'late' $true
            $successfulRefund = Get-RefundDetail $refund.refundId
            $budgetAfterSuccess = Get-PaymentDetail $payment.PaymentId
            Assert-AcEqual $success.Body.disposition 'ACCEPTED' 'trusted success converges the unknown retry attempt'
            Assert-Ac -Condition ([bool]$success.Body.reservationConvertedToSuccessNow) -Message 'accepted success converts the reservation once' -Actual $success.Body
            Assert-AcEqual $successReplay.Body.disposition 'DUPLICATE' 'success replay is duplicate'
            Assert-Ac -Condition (-not [bool]$successReplay.Body.reservationConvertedToSuccessNow) -Message 'success replay cannot convert budget again' -Actual $successReplay.Body
            Assert-AcEqual $late.Body.disposition 'CONFLICTING' 'late failure cannot reverse the successful refund'
            Assert-Ac -Condition (-not [bool]$late.Body.reservationReleasedNow -and -not [bool]$late.Body.reservationConvertedToSuccessNow) -Message 'late conflict cannot release or convert budget' -Actual $late.Body
            Assert-AcEqual $successfulRefund.status 'SUCCEEDED' 'success is the final refund fact'
            Assert-AcEqual $budgetAfterSuccess.refundBudget.succeededAmount.amountMinor '4000' 'successful amount is converted exactly once'
            Assert-AcEqual $budgetAfterSuccess.refundBudget.reservedAmount.amountMinor '0' 'successful branch leaves no active reservation'
            Assert-Ac -Condition (@($successfulRefund.attempts[0].notificationReceipts).Count -ge 1 -and @($successfulRefund.attempts[1].notificationReceipts).Count -ge 3) -Message 'retryable, unknown, success and conflict receipts remain queryable by attempt' -Actual $successfulRefund.attempts

            # Terminal-failure branch proves release is likewise a one-time transition.
            Set-AcClock $Context.BaseInstant.AddMinutes(3) | Out-Null
            $failedRefund = New-AcRefundRequest $Context $payment.PaymentId 20 'terminal-failure'
            $failedAttempt = New-AcRefundAttempt $Context $failedRefund.refundId 'terminal-failure'
            Submit-AcRefundAttempt $Context $failedRefund.refundId $failedAttempt.refundAttemptId 'terminal-failure' | Out-Null
            $failedBefore = Get-RefundDetail $failedRefund.refundId
            $failedChannelRefundId = $failedBefore.attempts[0].channelRefundId
            $terminalFailure = Send-AcRefundResult $Context $failedRefund.refundId $failedAttempt.refundAttemptId $failedChannelRefundId 20 'FAILED' 'terminal-failure' $true
            $failureReplay = Invoke-AcHttp POST '/api/channel/refund-results' @{
                channelId='C-001';notificationId=$terminalFailure.NotificationId;refundId=$failedRefund.refundId;refundAttemptId=$failedAttempt.refundAttemptId
                channelRefundId=$failedChannelRefundId;money=@{currency='CNY';amountMinor='2000'};result='FAILED'
                occurredAt=$Context.BaseInstant.AddMinutes(5).ToString('o');rawPayload="reference-refund-$($Context.Alias)-terminal-failure-FAILED"
            } @(200)
            $lateFailureSuccess = Send-AcRefundResult $Context $failedRefund.refundId $failedAttempt.refundAttemptId $failedChannelRefundId 20 'SUCCESS' 'terminal-failure-late-success' $true
            $failedAfter = Get-RefundDetail $failedRefund.refundId
            $finalBudget = Get-PaymentDetail $payment.PaymentId
            Assert-AcEqual $failedAfter.status 'FAILED' 'verified non-retryable failure is terminal'
            Assert-Ac -Condition ([bool]$terminalFailure.Body.reservationReleasedNow) -Message 'terminal failure releases its reservation once' -Actual $terminalFailure.Body
            Assert-AcEqual $failureReplay.Body.disposition 'DUPLICATE' 'terminal failure replay is duplicate'
            Assert-Ac -Condition (-not [bool]$failureReplay.Body.reservationReleasedNow) -Message 'terminal failure replay cannot release budget again' -Actual $failureReplay.Body
            Assert-AcEqual $lateFailureSuccess.Body.disposition 'CONFLICTING' 'late success cannot overwrite explicit terminal failure'
            Assert-AcEqual $finalBudget.refundBudget.succeededAmount.amountMinor '4000' 'terminal failure does not change prior successful refund total'
            Assert-AcEqual $finalBudget.refundBudget.reservedAmount.amountMinor '0' 'terminal failure releases its own reservation'
            Assert-AcEqual $finalBudget.refundBudget.availableAmount.amountMinor '6000' 'final budget reflects one conversion and one release'
            Assert-Ac -Condition (@($failedAfter.attempts[0].notificationReceipts).Count -ge 2) -Message 'terminal failure replay and late conflict receipts remain queryable' -Actual $failedAfter.attempts[0].notificationReceipts
            Add-AcObservation 'retryableUnknownSuccessRefund' $successfulRefund
            Add-AcObservation 'terminalFailureRefund' $failedAfter
            Add-AcObservation 'refundBudget' $finalBudget.refundBudget
        }
        'PAY-AC-100' {
            $created=New-AcPayment $Context 100 'CNY' 'receipts' 'receipts';$a1=New-AcPaymentAttempt $Context $created.paymentId 'a1';$a2=New-AcPaymentAttempt $Context $created.paymentId 'a2' 'parallel risk-approved attempt';Submit-AcPaymentAttempt $Context $created.paymentId $a1.paymentAttemptId 'a1'|Out-Null;Submit-AcPaymentAttempt $Context $created.paymentId $a2.paymentAttemptId 'a2'|Out-Null
            $first=Send-AcPaymentResult $Context $created.paymentId $a1.paymentAttemptId 100 'SUCCESS' 'first' $true
            $duplicate=Invoke-AcHttp POST '/api/channel/payment-results' @{channelId='C-001';notificationId=$first.NotificationId;paymentId=$created.paymentId;paymentAttemptId=$a1.paymentAttemptId;channelTransactionId=$first.TransactionId;money=@{currency='CNY';amountMinor='10000'};result='SUCCESS';occurredAt=$first.OccurredAt;rawPayload=$first.RawPayload} @(200)
            $invalid=Send-AcPaymentResult $Context $created.paymentId $a1.paymentAttemptId 99 'SUCCESS' 'invalid' $false
            $unknownAttempt='01900000-0000-7000-8000-000000000099';$unknown=Send-AcPaymentResult $Context $created.paymentId $unknownAttempt 100 'SUCCESS' 'unknown-ref' $true
            $unknownReceipt=(Invoke-AcHttp GET "/api/channel/payment-results/$($unknown.NotificationId)/receipts?channelId=C-001" $null @(200)).Body
            $unknownPaymentId='01900000-0000-7000-8000-000000000098';$unknownPaymentAttempt='01900000-0000-7000-8000-000000000097'
            $unassociated=Send-AcPaymentResult $Context $unknownPaymentId $unknownPaymentAttempt 100 'SUCCESS' 'unknown-payment' $true
            $unassociatedReceipt=(Invoke-AcHttp GET "/api/channel/payment-results/$($unassociated.NotificationId)/receipts?channelId=C-001" $null @(200)).Body
            $second=Send-AcPaymentResult $Context $created.paymentId $a2.paymentAttemptId 100 'SUCCESS' 'second' $true
            $latePayment=New-AcPayment $Context 25 'CNY' 'late-receipt' 'late-receipt' $Context.BaseInstant.AddMinutes(2)
            Invoke-AcHttp POST '/api/reference-fixtures/payment-channel-script' @{channelId='C-001';script='REJECT_ON_SUBMIT'} @(200)|Out-Null
            $lateAttempt=New-AcPaymentAttempt $Context $latePayment.paymentId 'late-receipt';Submit-AcPaymentAttempt $Context $latePayment.paymentId $lateAttempt.paymentAttemptId 'late-receipt'|Out-Null
            Set-AcClock $Context.BaseInstant.AddMinutes(3)|Out-Null;Invoke-AcHttp POST '/api/reference-fixtures/maintenance' @{action='PAYMENT_EXPIRY'} @(200)|Out-Null
            $late=Send-AcPaymentResult $Context $latePayment.paymentId $lateAttempt.paymentAttemptId 25 'SUCCESS' 'late-receipt' $true
            $detail=Get-PaymentDetail $created.paymentId
            $lateDetail=Get-PaymentDetail $latePayment.paymentId
            Assert-Ac -Condition ($detail.attempts.Count -eq 2 -and @($detail.attempts.submissionReceipts).Count -ge 2) -Message 'independent attempts and submission receipts are queryable' -Actual $detail.attempts
            Assert-Ac -Condition ($detail.notificationReceiveCount -ge 5) -Message 'all result receptions including unknown reference are counted' -Actual $detail.notificationReceiveCount
            Assert-AcEqual $first.Body.disposition 'ACCEPTED' 'first success is accepted'
            Assert-AcEqual $duplicate.Body.disposition 'DUPLICATE' 'same receipt is duplicate'
            Assert-AcEqual $invalid.Body.disposition 'REJECTED_INVALID' 'invalid receipt is rejected explicitly'
            Assert-AcEqual $unknown.Body.disposition 'UNKNOWN_REFERENCE' 'unknown attempt reference has explicit disposition'
            Assert-AcEqual $unknownReceipt.totalReceiveCount 1 'unknown-attempt receipt has an exact external reception count'
            Assert-AcEqual $unknownReceipt.receipts.Count 1 'unknown-attempt external identity resolves one payload receipt'
            Assert-AcEqual $unknownReceipt.receipts[0].disposition 'UNKNOWN_REFERENCE' 'unknown-attempt receipt remains queryable by external identity'
            Assert-AcEqual $unknownReceipt.receipts[0].paymentAttemptId $unknownAttempt 'unknown-attempt receipt retains the supplied association reference'
            Assert-AcEqual $unknownReceipt.receipts[0].rawEvidence $unknown.RawPayload 'unknown-attempt receipt retains canonical raw evidence'
            Assert-AcEqual $unassociated.Body.disposition 'UNKNOWN_REFERENCE' 'unknown payment reference is accepted as an unassociated receipt'
            Assert-AcEqual $unassociated.Body.receipt.resource.resourceType 'ChannelResultReceipt' 'unassociated callback Operation points at the authoritative receipt'
            Assert-AcEqual $unassociatedReceipt.totalReceiveCount 1 'unknown-payment receipt is counted independently'
            Assert-AcEqual $unassociatedReceipt.receipts[0].paymentId $unknownPaymentId 'unknown-payment receipt retains the supplied external payment reference'
            Assert-AcEqual $unassociatedReceipt.receipts[0].verification 'VERIFIED' 'trusted verifier conclusion is persisted for unknown references'
            Assert-AcEqual $late.Body.disposition 'LATE' 'closed-payment success is retained as late evidence'
            Assert-AcEqual $lateDetail.finality 'REVIEW_REQUIRED' 'late receipt opens a review-required payment'
            Assert-AcEqual $second.Body.disposition 'CONFLICTING' 'second accepted success is conflicting'
            Assert-AcEqual $detail.merchantSuccessNotificationIntentCount 1 'payment keeps one accepted success fact'
            Add-AcObservation 'paymentAttemptsAndReceipts' $detail;Add-AcObservation 'unknownReferenceReceipts' @($unknownReceipt,$unassociatedReceipt);Add-AcObservation 'latePaymentReceipt' $lateDetail
        }
        'PAY-AC-101' {
            $defaultPolicy = (Invoke-AcHttp GET '/api/reference-fixtures/policy' $null @(200)).Body.policy
            Assert-AcEqual ([decimal]$defaultPolicy.feeRate) ([decimal]0.006) 'reference default feeRate is explicit input, not production policy'
            Assert-AcEqual $defaultPolicy.roundingMode 'HALF_UP' 'reference default roundingMode is explicit input'
            $defaultPayment = New-AcSucceededPayment $Context 100 'default'
            $defaultDetail = Get-PaymentDetail $defaultPayment.PaymentId
            Assert-AcEqual ([decimal]$defaultDetail.feeSnapshot.feeRate) ([decimal]0.006) 'default fee snapshot freezes the effective default rate'
            Assert-AcEqual $defaultDetail.feeSnapshot.roundingMode 'HALF_UP' 'default fee snapshot freezes HALF_UP'
            Assert-AcEqual $defaultDetail.feeSnapshot.feeMoney.amountMinor '60' 'default fee snapshot deterministically calculates CNY 0.60'

            $overrideContext = New-AcChildContext $Context 'override' 1
            Set-AcClock $overrideContext.BaseInstant | Out-Null
            $overridePolicy = (Invoke-AcHttp POST '/api/reference-fixtures/policy' @{
                paymentExpiry='PT10M';unknownResultReviewAfter='PT2M';refundWindow='P1D';maxRefundAttempts=1
                feeRate=0.008;roundingMode='DOWN';enabledCurrencies=@('CNY');currencyPrecisions=@{CNY=2}
                defaultPageSize=1;maxPageSize=2
            } @(200)).Body.policy
            Assert-AcEqual $overridePolicy.paymentExpiry 'PT10M' 'payment expiry override is accepted as fixture input'
            Assert-AcEqual $overridePolicy.unknownResultReviewAfter 'PT2M' 'unknown review threshold override is accepted as fixture input'
            Assert-AcEqual $overridePolicy.refundWindow 'P1D' 'refund window override is accepted as fixture input'
            Assert-AcEqual $overridePolicy.maxRefundAttempts 1 'refund attempt limit override is accepted as fixture input'
            Assert-AcEqual $overridePolicy.defaultPageSize 1 'default page size override is accepted as fixture input'
            Assert-AcEqual $overridePolicy.maxPageSize 2 'maximum page size override is accepted as fixture input'

            $overridePayment = New-AcSucceededPayment $overrideContext 100 'override'
            $overrideDetail = Get-PaymentDetail $overridePayment.PaymentId
            Assert-AcEqual ([DateTimeOffset]$overrideDetail.expiresAt) $overrideContext.BaseInstant.AddMinutes(10) 'paymentExpiry is consumed when the payment expiry fact is created'
            Assert-AcEqual ([decimal]$overrideDetail.feeSnapshot.feeRate) ([decimal]0.008) 'override fee snapshot freezes the effective rate'
            Assert-AcEqual $overrideDetail.feeSnapshot.roundingMode 'DOWN' 'override fee snapshot freezes the effective rounding mode'
            Assert-AcEqual $overrideDetail.feeSnapshot.feeMoney.amountMinor '80' 'override fee result is deterministic in minor units'

            $secondPayment = New-AcPayment $overrideContext 5 'CNY' 'page-second' 'page-second'
            $defaultPage = (Invoke-AcHttp POST '/api/payments/search' @{merchantId=$overrideContext.MerchantId} @(200)).Body
            $maxPage = (Invoke-AcHttp POST '/api/payments/search' @{merchantId=$overrideContext.MerchantId;pageSize=2} @(200)).Body
            $overMaxPage = Invoke-AcHttp POST '/api/payments/search' @{merchantId=$overrideContext.MerchantId;pageSize=3} @(400)
            Assert-AcEqual $defaultPage.pageSize 1 'omitted pageSize consumes ReferencePolicy.defaultPageSize'
            Assert-AcEqual $defaultPage.items.Count 1 'default page contains exactly one authoritative item'
            Assert-Ac -Condition (-not [string]::IsNullOrWhiteSpace([string]$defaultPage.nextCursor)) -Message 'default page exposes a cursor when another item exists' -Actual $defaultPage
            Assert-AcEqual $maxPage.pageSize 2 'explicit pageSize at the configured maximum is accepted'
            Assert-AcEqual $maxPage.items.Count 2 'configured maximum page size is consumed by the authoritative query'
            Assert-AcEqual (Get-AcErrorCode $overMaxPage.Body) 'VALIDATION_ERROR' 'pageSize above ReferencePolicy.maxPageSize is rejected'

            Set-AcClock $overrideContext.BaseInstant.AddMinutes(3) | Out-Null
            $unknownRefund = New-AcRefundRequest $overrideContext $overridePayment.PaymentId 10 'unknown-policy'
            $unknownAttempt = New-AcRefundAttempt $overrideContext $unknownRefund.refundId 'unknown-policy'
            Submit-AcRefundAttempt $overrideContext $unknownRefund.refundId $unknownAttempt.refundAttemptId 'unknown-policy' | Out-Null
            $unknownBefore = Get-RefundDetail $unknownRefund.refundId
            $unknownChannelRefundId = $unknownBefore.attempts[0].channelRefundId
            Send-AcRefundResult $overrideContext $unknownRefund.refundId $unknownAttempt.refundAttemptId $unknownChannelRefundId 10 'UNKNOWN' 'unknown-policy' $true | Out-Null
            Assert-AcEqual ([DateTimeOffset](Get-RefundDetail $unknownRefund.refundId).attempts[0].reviewAfterAt) $overrideContext.BaseInstant.AddMinutes(5) 'unknown review threshold is frozen into the attempt'
            Set-AcClock $overrideContext.BaseInstant.AddMinutes(4) | Out-Null
            $earlyReview = (Invoke-AcHttp POST '/api/reference-fixtures/maintenance' @{action='REFUND_UNKNOWN_REVIEW'} @(200)).Body
            Assert-AcEqual $earlyReview.changedCount 0 'unknown result does not enter threshold review one minute early'
            Assert-AcEqual (Get-RefundDetail $unknownRefund.refundId).finality 'NON_FINAL' 'early unknown result remains non-final'
            Set-AcClock $overrideContext.BaseInstant.AddMinutes(5) | Out-Null
            $dueReview = (Invoke-AcHttp POST '/api/reference-fixtures/maintenance' @{action='REFUND_UNKNOWN_REVIEW'} @(200)).Body
            $unknownAfter = Get-RefundDetail $unknownRefund.refundId
            Assert-AcEqual $dueReview.changedCount 1 'unknown result enters review at the overridden two-minute threshold'
            Assert-AcEqual $unknownAfter.finality 'REVIEW_REQUIRED' 'threshold processing exposes review-required finality'
            Assert-Ac -Condition ([bool]$unknownAfter.reservationActive) -Message 'unknown review continues to occupy its reservation' -Actual $unknownAfter

            $limitedRefund = New-AcRefundRequest $overrideContext $overridePayment.PaymentId 10 'attempt-limit'
            $limitedAttempt = New-AcRefundAttempt $overrideContext $limitedRefund.refundId 'attempt-limit-1'
            Submit-AcRefundAttempt $overrideContext $limitedRefund.refundId $limitedAttempt.refundAttemptId 'attempt-limit-1' | Out-Null
            $limitedBefore = Get-RefundDetail $limitedRefund.refundId
            Send-AcRefundResult $overrideContext $limitedRefund.refundId $limitedAttempt.refundAttemptId $limitedBefore.attempts[0].channelRefundId 10 'RETRYABLE_FAILURE' 'attempt-limit' $true | Out-Null
            $overAttemptLimit = Invoke-AcHttp POST "/api/refunds/$($limitedRefund.refundId)/attempts" @{idempotencyKey="$($overrideContext.Alias)-refund-attempt-limit-2"} @(400,409)
            $limitedAfter = Get-RefundDetail $limitedRefund.refundId
            Assert-AcEqual (Get-AcErrorCode $overAttemptLimit.Body) 'VALIDATION_ERROR' 'maxRefundAttempts rejects a second attempt'
            Assert-AcEqual $limitedAfter.attempts.Count 1 'attempt-limit rejection creates no extra attempt'
            Assert-AcEqual $limitedAfter.status 'PROCESSING' 'rejected extra attempt does not release or finalize the Refund'

            Set-AcClock $overrideContext.BaseInstant.AddDays(2) | Out-Null
            $expiredRefund = Invoke-AcHttp POST '/api/refunds' @{
                merchantId=$overrideContext.MerchantId;idempotencyKey="$($overrideContext.Alias)-expired-refund"
                merchantRefundNo="$($overrideContext.Alias)-expired-refund";paymentId=$overridePayment.PaymentId
                money=@{currency='CNY';amountMinor='100'};reason='outside explicit P1D reference refund window'
            } @(400)
            Assert-AcEqual (Get-AcErrorCode $expiredRefund.Body) 'VALIDATION_ERROR' 'refundWindow is consumed and rejects a request after one day'
            $expiredSearch = (Invoke-AcHttp POST '/api/refunds/search' @{merchantId=$overrideContext.MerchantId;merchantRefundId="$($overrideContext.Alias)-expired-refund";pageSize=1} @(200)).Body
            Assert-AcEqual $expiredSearch.items.Count 0 'expired refund rejection creates no Refund side effect'

            Add-AcObservation 'policyComparison' @{
                default=$defaultPolicy;override=$overridePolicy;defaultFee=$defaultDetail.feeSnapshot;overrideFee=$overrideDetail.feeSnapshot
                expiry=$overrideDetail.expiresAt;pages=@{default=$defaultPage;maximum=$maxPage};unknownRefund=$unknownAfter
                limitedRefund=$limitedAfter;expiredRefundError=$expiredRefund.Body;secondPaymentId=$secondPayment.paymentId
            }
        }
        'PAY-AC-102' {
            $summary=Invoke-PayAcCleanLoop -Context $Context -Suffix 'scenario102'
            Assert-AcEqual $summary.paymentStatus 'SUCCEEDED' 'sandbox loop completes payment'
            Assert-AcEqual $summary.refundStatus 'SUCCEEDED' 'sandbox loop completes refund'
            Assert-AcEqual $summary.reconciliationStatus 'COMPLETED' 'sandbox loop completes reconciliation'
            Assert-AcEqual $summary.settlementStatus 'SETTLED' 'sandbox loop completes settlement'
            Assert-AcEqual $summary.stableApiError.code 'VALIDATION_ERROR' 'sandbox loop exposes a stable synchronous error code'
            Assert-Ac -Condition ([bool]$summary.ordering.timelineOrderValid) -Message 'sandbox loop validates authoritative timeline ordering' -Actual $summary.ordering
            Assert-Ac -Condition ([bool]$summary.correlations.paymentAttemptLinked -and [bool]$summary.correlations.refundPaymentLinked -and [bool]$summary.correlations.settlementSourcesLinked) -Message 'sandbox loop preserves cross-resource associations' -Actual $summary.correlations
            Assert-AcEqual $summary.sideEffects.paymentResultReplayDisposition 'DUPLICATE' 'payment external identity replay is duplicate-only'
            Assert-AcEqual $summary.sideEffects.refundResultReplayDisposition 'DUPLICATE' 'refund external identity replay is duplicate-only'
            Assert-AcEqual $summary.sideEffects.paymentSuccessFacts 1 'payment replay leaves one success fact'
            Assert-AcEqual $summary.sideEffects.refundSuccessFacts 1 'refund replay leaves one success fact'
            Assert-AcEqual $summary.sideEffects.settlementSuccessFacts 1 'settlement replay leaves one success fact'
            Add-AcObservation 'cleanLoopNormalized' $summary
        }
        'PAY-AC-103' {
            $flow=New-AcSucceededPayment $Context 100 'comparison';$detail=Get-PaymentDetail $flow.PaymentId
            $invalid=Invoke-AcHttp POST '/api/payments' @{merchantId=$Context.MerchantId;merchantOrderNumber="$($Context.Alias)-invalid";idempotencyKey="$($Context.Alias)-invalid";money=@{currency='USD';amountMinor='100'};paymentMethod='CARD'} @(400)
            $timeline=(Invoke-AcHttp GET "/api/payments/$($flow.PaymentId)/timeline?pageSize=100" $null @(200)).Body
            $normalized=[ordered]@{scenarioAlias='PAY-AC-103';money=$detail.money;paymentStatus=$detail.status;paymentFinality=$detail.finality;invalidCurrencyError=$invalid.Body;attemptStatus=$detail.attempts[0].status;paymentId=$detail.paymentId;attemptId=$detail.attempts[0].paymentAttemptId;channelTransactionId=$detail.channelTransactionId;timelineEventIds=@($timeline.items.eventId);successFactCount=if($detail.successFactFormed){1}else{0};routeAdapter='CAP4K';wowAssertion=$false}
            Assert-AcEqual $normalized.paymentStatus 'SUCCEEDED' 'CAP4K normalized status matches unified contract'
            Assert-AcEqual $normalized.paymentFinality 'FINAL' 'CAP4K normalized finality matches unified contract'
            Assert-AcEqual $normalized.money.currency 'CNY' 'normalized Money currency comes from the HTTP resource'
            Assert-AcEqual $normalized.money.amountMinor '10000' 'normalized Money amount comes from the HTTP resource'
            Assert-AcEqual $normalized.attemptStatus 'SUCCEEDED' 'normalized attempt status matches unified contract'
            Assert-AcEqual (Get-AcErrorCode $normalized.invalidCurrencyError) 'VALIDATION_ERROR' 'CAP4K normalized ApiError matches unified contract'
            Assert-Ac -Condition ($normalized.timelineEventIds.Count -ge 3) -Message 'normalized evidence retains authoritative association and ordering inputs' -Actual $normalized.timelineEventIds
            Assert-AcEqual $normalized.successFactCount 1 'CAP4K normalized side effect is one success fact'
            Assert-Ac -Condition (-not $normalized.wowAssertion) -Message 'evidence is comparison input and does not claim WOW passed' -Actual $normalized
            Add-AcObservation 'crossBackendComparisonInput' $normalized
        }
        default { throw "Unknown protocol/closure scenario $Id" }
    }
}

function Invoke-PayAcCleanLoop {
    param([Parameter(Mandatory)]$Context, [string]$Suffix = 'clean')
    $payment=New-AcSucceededPayment $Context 100 "$Suffix-payment"
    $paymentCreateReplay=New-AcPayment $Context 100 'CNY' "order-$Suffix-payment" "create-$Suffix-payment"
    $paymentResultReplay=Invoke-AcHttp POST '/api/channel/payment-results' @{
        channelId='C-001';notificationId=$payment.Result.NotificationId;paymentId=$payment.PaymentId;paymentAttemptId=$payment.Attempt.paymentAttemptId
        channelTransactionId=$payment.TransactionId;money=@{currency='CNY';amountMinor='10000'};result='SUCCESS';occurredAt=$payment.OccurredAt;rawPayload=$payment.Result.RawPayload
    } @(200)
    $refund=New-AcSucceededRefund $Context $payment.PaymentId 20 "$Suffix-refund"
    $refundRequestReplay=New-AcRefundRequest $Context $payment.PaymentId 20 "$Suffix-refund"
    $refundResultReplay=Invoke-AcHttp POST '/api/channel/refund-results' @{
        channelId='C-001';notificationId=$refund.Result.NotificationId;refundId=$refund.RefundId;refundAttemptId=$refund.Attempt.refundAttemptId
        channelRefundId=$refund.ChannelRefundId;money=@{currency='CNY';amountMinor='2000'};result='SUCCESS'
        occurredAt=$Context.BaseInstant.AddMinutes(5).ToString('o');rawPayload="reference-refund-$($Context.Alias)-$Suffix-refund-SUCCESS"
    } @(200)
    $records=@(
        (New-AcBillRecord "$($Context.Alias)-$Suffix-payment" 'PAYMENT' $payment.TransactionId 100 $payment.OccurredAt),
        (New-AcBillRecord "$($Context.Alias)-$Suffix-refund" 'REFUND' $refund.ChannelRefundId 20 $refund.OccurredAt)
    )
    $bill=Register-AcBillAndSignal $Context $records '1' $Suffix
    $billSignalReplay=(Invoke-AcHttp POST "/api/reference-fixtures/bills/$($bill.BillIdentity)/signals" @{
        channelId='C-001';businessDate=$Context.BusinessDate;currency='CNY';businessTimezone='Asia/Shanghai'
        signalIdentity="SIGNAL-$($bill.BillIdentity)-1";announcedRevision='1';publishedAt=$Context.BaseInstant.AddHours(12).ToString('o')
    } @(200)).Body
    $prepared=New-AcPreparedSettlement $Context $Suffix
    Confirm-AcSettlement $Context $prepared.settlementId $Suffix|Out-Null
    $execution=Start-AcSettlementExecution $Context $prepared.settlementId $Suffix
    $before=Get-SettlementDetail $prepared.settlementId
    Send-AcSettlementResult $Context $prepared.settlementId $execution ([decimal]$before.netMoney.amountMinor/100) 'SUCCESS' $Suffix|Out-Null
    $settlementResultReplay=Invoke-AcHttp POST '/api/channel/settlement-results' @{
        channelId='C-001';notificationId="SN-$($Context.Alias)-$Suffix-SUCCESS";settlementId=$prepared.settlementId
        executionAttemptId=$execution.attemptId;executionGroupIdentity=$execution.executionGroupIdentity;requestIdentity=$execution.requestIdentity
        externalSettlementIdentity="STL-$($execution.requestIdentity)";money=@{currency='CNY';amountMinor=$before.netMoney.amountMinor};result='SUCCESS';resultCode='SUCCESS'
        occurredAt=$Context.BaseInstant.AddHours(13).ToString('o');receivedAt=$Context.BaseInstant.AddHours(13).AddSeconds(30).ToString('o')
        rawPayload="reference-settlement-$($Context.Alias)-$Suffix-SUCCESS"
    } @(200)
    $invalid=Invoke-AcHttp POST '/api/payments' @{
        merchantId=$Context.MerchantId;merchantOrderNumber="$($Context.Alias)-$Suffix-invalid";idempotencyKey="$($Context.Alias)-$Suffix-invalid"
        money=@{currency='CNY';amountMinor='0'};paymentMethod='CARD'
    } @(400)
    $settled=Get-SettlementDetail $prepared.settlementId
    $paymentDetail=Get-PaymentDetail $payment.PaymentId
    $refundDetail=Get-RefundDetail $refund.RefundId
    $timeline=(Invoke-AcHttp GET "/api/payments/$($payment.PaymentId)/timeline?pageSize=100" $null @(200)).Body
    $notifications=Search-Notifications @{merchantId=$Context.MerchantId;pageSize=100}
    $timelineKeys=@($timeline.items|ForEach-Object{"$([DateTimeOffset]::Parse([string]$_.recordedAt).ToUniversalTime().ToString('o'))|$($_.eventId)"})
    $sortedTimelineKeys=@($timelineKeys);[Array]::Sort($sortedTimelineKeys,[StringComparer]::Ordinal)
    $eventTypeCounts=@($timeline.items|Group-Object eventType|Sort-Object Name|ForEach-Object{"$($_.Name):$($_.Count)"})
    $includedLines=@($settled.lines|Where-Object decision -eq 'INCLUDED')
    $paymentSourceLines=@($includedLines|Where-Object { $_.sourceKind -eq 'PAYMENT' -and $_.paymentId -eq $payment.PaymentId })
    $refundSourceLines=@($includedLines|Where-Object { $_.sourceKind -eq 'REFUND' -and $_.refundId -eq $refund.RefundId })
    $stableErrorCode=Get-AcErrorCode $invalid.Body

    Assert-AcEqual $paymentCreateReplay.receipt.operationId $payment.Created.receipt.operationId 'clean loop payment command replay reuses the original Operation'
    Assert-AcEqual $paymentCreateReplay.receipt.acceptanceStatus 'ALREADY_ACCEPTED' 'clean loop payment command replay is ALREADY_ACCEPTED'
    Assert-AcEqual $refundRequestReplay.receipt.operationId $refund.Refund.receipt.operationId 'clean loop refund command replay reuses the original Operation'
    Assert-AcEqual $refundRequestReplay.receipt.acceptanceStatus 'ALREADY_ACCEPTED' 'clean loop refund command replay is ALREADY_ACCEPTED'
    Assert-AcEqual $paymentResultReplay.Body.disposition 'DUPLICATE' 'clean loop payment callback replay is DUPLICATE'
    Assert-AcEqual $refundResultReplay.Body.disposition 'DUPLICATE' 'clean loop refund callback replay is DUPLICATE'
    Assert-Ac -Condition (-not [bool]$settlementResultReplay.Body.settledFactFormedNow) -Message 'clean loop settlement callback replay forms no second success fact' -Actual $settlementResultReplay.Body
    Assert-AcEqual $billSignalReplay.runId $bill.Signal.runId 'clean loop bill signal replay reuses the original run'
    Assert-Ac -Condition ([bool]$billSignalReplay.idempotentReplay) -Message 'clean loop bill signal replay is explicitly idempotent' -Actual $billSignalReplay
    Assert-Ac -Condition (($timelineKeys -join ',') -eq ($sortedTimelineKeys -join ',')) -Message 'clean loop timeline obeys recordedAt ASC,eventId ASC' -Actual $timelineKeys
    Assert-AcEqual @($timeline.items.eventId|Select-Object -Unique).Count $timeline.items.Count 'clean loop timeline event ids are unique'
    Assert-Ac -Condition ($paymentSourceLines.Count -eq 1 -and $refundSourceLines.Count -eq 1) -Message 'clean loop settlement retains payment and refund source associations' -Actual $includedLines
    return [ordered]@{
        paymentStatus=$paymentDetail.status
        paymentFinality=$paymentDetail.finality
        paymentAttemptStatus=$paymentDetail.attempts[0].status
        refundStatus=$refundDetail.status
        refundFinality=$refundDetail.finality
        refundAttemptStatus=$refundDetail.attempts[0].status
        reconciliationStatus=$bill.Run.status
        settlementStatus=$settled.status
        settlementFinality=$settled.finality
        settlementExecutionStatus=$settled.attempts[0].status
        settlementNetMinor=$settled.netMoney.amountMinor
        stableApiError=[ordered]@{code=$stableErrorCode;retryable=[bool]$invalid.Body.retryable;details=$invalid.Body.details}
        correlations=[ordered]@{
            paymentAttemptLinked=($paymentDetail.attempts.Count -eq 1 -and $paymentDetail.attempts[0].paymentAttemptId -eq $payment.Attempt.paymentAttemptId -and $paymentDetail.channelTransactionId -eq $payment.TransactionId)
            refundPaymentLinked=($refundDetail.paymentId -eq $payment.PaymentId -and $refundDetail.attempts[0].refundAttemptId -eq $refund.Attempt.refundAttemptId -and $refundDetail.channelRefundId -eq $refund.ChannelRefundId)
            billRevisionRunLinked=(@($timeline.items|Where-Object eventType -eq 'BILL_REVISION').Count -eq 1 -and @($timeline.items|Where-Object eventType -eq 'RECONCILIATION_RUN').Count -eq 1)
            settlementSourcesLinked=($paymentSourceLines.Count -eq 1 -and $refundSourceLines.Count -eq 1)
            timelinePaymentRefsComplete=(@($timeline.items|Where-Object {$_.refs.paymentId -ne $payment.PaymentId}).Count -eq 0)
            settlementExecutionLinked=($settled.attempts.Count -eq 1 -and $settled.attempts[0].attemptId -eq $execution.attemptId)
        }
        ordering=[ordered]@{
            comparator='recordedAt ASC,eventId ASC'
            timelineOrderValid=(($timelineKeys -join ',') -eq ($sortedTimelineKeys -join ','))
            uniqueEventIds=(@($timeline.items.eventId|Select-Object -Unique).Count -eq $timeline.items.Count)
            eventTypeCounts=$eventTypeCounts
        }
        sideEffects=[ordered]@{
            paymentSuccessFacts=if($paymentDetail.successFactFormed){1}else{0}
            refundSuccessFacts=if($refundDetail.successFactFormed){1}else{0}
            settlementSuccessFacts=if($settled.settledFactFormed){1}else{0}
            paymentNotificationIntentCount=[int]$paymentDetail.merchantSuccessNotificationIntentCount
            paymentResultReceiveCount=[int]$paymentDetail.attempts[0].notificationReceiveCount
            refundResultReceiveCount=[int]$refundDetail.attempts[0].notificationReceiveCount
            settlementResultReceiveCount=[int]$settled.attempts[0].notificationReceiveCount
            paymentResultReplayDisposition=[string]$paymentResultReplay.Body.disposition
            refundResultReplayDisposition=[string]$refundResultReplay.Body.disposition
            settlementReplayFormedFact=[bool]$settlementResultReplay.Body.settledFactFormedNow
            notificationSources=@($notifications.items.sourceKind|Sort-Object -Unique)
        }
    }
}

function Invoke-PayAcScenario {
    param([Parameter(Mandatory)][string]$ScenarioId)
    $context=Initialize-AcScenario $ScenarioId
    if($ScenarioId -match '^PAY-AC-0(0[1-9]|1[0-7])$'){Invoke-PaymentPayAcScenario $ScenarioId $context;return}
    if($ScenarioId -match '^PAY-AC-02[0-9]$'){Invoke-RefundPayAcScenario $ScenarioId $context;return}
    if($ScenarioId -match '^PAY-AC-04[0-7]$'){Invoke-ReconciliationPayAcScenario $ScenarioId $context;return}
    if($ScenarioId -match '^PAY-AC-06[0-8]$'){Invoke-SettlementPayAcScenario $ScenarioId $context;return}
    if($ScenarioId -match '^PAY-AC-08[0-8]$'){Invoke-ScopeNotificationTracePayAcScenario $ScenarioId $context;return}
    if($ScenarioId -match '^PAY-AC-(09[0-9]|10[0-3])$'){Invoke-ProtocolClosurePayAcScenario $ScenarioId $context;return}
    throw "No PAY-AC scenario implementation for $ScenarioId"
}

Export-ModuleMember -Function Invoke-PayAcScenario,Invoke-PayAcCleanLoop
